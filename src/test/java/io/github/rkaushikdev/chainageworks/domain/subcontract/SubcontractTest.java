package io.github.rkaushikdev.chainageworks.domain.subcontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SubcontractTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final Subcontract SUBCONTRACT = Subcontract.build(SubcontractInput.of(
                    "SC-01", "bulk earthworks and capping", "Dowsett Plant Hire", gbp(250_000), d("2024-11-04"))
            .withPackages(List.of("WP-01", "WP-02"))
            .withCodes(List.of(ItemCode.parse("E423.1"), ItemCode.parse("E532"))));

    private static final List<PaymentApplication> APPLICATIONS = List.of(
            new PaymentApplication("SA-01", "SC-01", 1, d("2025-01-25"), gbp(40_000), gbp(0)),
            new PaymentApplication("SA-02", "SC-01", 2, d("2025-02-25"), gbp(90_000), gbp(36_100)),
            new PaymentApplication("SA-03", "SC-01", 3, d("2025-03-25"), gbp(120_000), gbp(85_500)));

    private static final List<PaymentNotice> NOTICES = List.of(
            new PaymentNotice("SA-01", d("2025-02-27"), gbp(38_000)),
            new PaymentNotice("SA-03", d("2025-04-28"), gbp(100_000)));

    private static final Subcontract.Inputs INPUTS = Subcontract.Inputs.of(APPLICATIONS, NOTICES, List.of());

    @Nested
    class Build {

        @Test
        void keepsThePackagesAndThePricedItemsApart() {
            assertThat(SUBCONTRACT.packageIds()).containsExactly("WP-01", "WP-02");
            assertThat(SUBCONTRACT.codes()).hasSize(2);
        }

        @Test
        void fallsBackToTheStatutoryPaymentTerms() {
            assertThat(SUBCONTRACT.terms().dueDays()).isEqualTo(30);
        }

        @Test
        void takesPaymentTermsOfItsOwn() {
            Subcontract own = Subcontract.build(
                    SubcontractInput.of("SC-02", "drainage", "Halliwell Civils", gbp(80_000), d("2024-12-02"))
                            .withTerms(PaymentTerms.build(
                                    PaymentTerms.DEFAULT.withDueDays(21).withRetention(Percent.of(3)))));
            assertThat(own.terms().dueDays()).isEqualTo(21);
            assertThat(own.packageIds()).isEmpty();
        }

        @Test
        void refusesASubcontractWithNoContractorNamed() {
            assertThatThrownBy(() -> Subcontract.build(
                            SubcontractInput.of("SC-03", "fencing", "   ", gbp(10_000), d("2025-01-06"))))
                    .hasMessageContaining("name the subcontractor");
        }

        @Test
        void refusesANegativeOrderValue() {
            assertThatThrownBy(() -> Subcontract.build(
                            SubcontractInput.of("SC-04", "fencing", "Halliwell Civils", gbp(-1), d("2025-01-06"))))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void refusesTheSameBillItemTwiceOnOneOrder() {
            assertThatThrownBy(() -> Subcontract.build(
                            SubcontractInput.of("SC-05", "fencing", "Halliwell Civils", gbp(10_000), d("2025-01-06"))
                                    .withCodes(List.of(ItemCode.parse("E423.1"), ItemCode.parse("E423.1")))))
                    .hasMessageContaining("on one subcontract twice");
        }
    }

    @Nested
    class WhatASubcontractCovers {

        @Test
        void answersOnAPackage() {
            assertThat(SUBCONTRACT.coversPackage("WP-01")).isTrue();
            assertThat(SUBCONTRACT.coversPackage("WP-09")).isFalse();
        }

        @Test
        void answersOnABillItem() {
            assertThat(SUBCONTRACT.coversCode(ItemCode.parse("E532"))).isTrue();
            assertThat(SUBCONTRACT.coversCode(ItemCode.parse("E533"))).isFalse();
        }
    }

    @Nested
    class Position {

        @Test
        void runsTheApplicationsInPeriodOrderUpToTheDate() {
            Subcontract.Position position = SUBCONTRACT.position(INPUTS, d("2025-03-25"));
            assertThat(position.applications()).isEqualTo(3);
            assertThat(position.certifiedToDate().major()).isEqualTo(95_000);
            assertThat(position.paidToDate().major()).isEqualTo(95_000);
            assertThat(position.remaining().major()).isEqualTo(155_000);
        }

        @Test
        void namesTheApplicationThePayerLetDefault() {
            assertThat(SUBCONTRACT.position(INPUTS, d("2025-03-25")).defaulted())
                    .containsExactly("SA-02");
        }

        @Test
        void leavesOutAnApplicationMadeAfterTheDate() {
            Subcontract.Position position = SUBCONTRACT.position(INPUTS, d("2025-02-25"));
            assertThat(position.applications()).isEqualTo(2);
            assertThat(position.certifiedToDate().major()).isEqualTo(85_500);
            assertThat(position.remaining().major()).isEqualTo(164_500);
        }

        @Test
        void ignoresApplicationsMadeAgainstAnotherOrder() {
            PaymentApplication first = APPLICATIONS.get(0);
            PaymentApplication other = new PaymentApplication(
                    "SA-99", "SC-02", first.periodNumber(), first.appliedOn(), first.gross(), first.previouslyPaid());
            List<PaymentApplication> all = new ArrayList<>(APPLICATIONS);
            all.add(other);
            assertThat(SUBCONTRACT
                            .position(INPUTS.withApplications(all), d("2025-03-25"))
                            .applications())
                    .isEqualTo(3);
        }

        @Test
        void letsAPayLessNoticeCutWhatIsPaidWithoutMovingWhatIsCertified() {
            Subcontract.Position position = SUBCONTRACT.position(
                    INPUTS.withPayLess(List.of(new PayLessNotice(
                            "SA-03", d("2025-05-30"), gbp(2000), "capping laid outside tolerance at 12+500"))),
                    d("2025-03-25"));
            assertThat(position.certifiedToDate().major()).isEqualTo(95_000);
            assertThat(position.paidToDate().major()).isEqualTo(87_500);
        }

        @Test
        void isEmptyOnAnOrderNobodyHasAppliedAgainst() {
            Subcontract.Position position =
                    SUBCONTRACT.position(Subcontract.Inputs.of(List.of(), List.of(), List.of()), d("2025-03-25"));
            assertThat(position.applications()).isZero();
            assertThat(position.certifiedToDate().minor()).isZero();
            assertThat(position.remaining().major()).isEqualTo(250_000);
        }
    }

    @Nested
    class OverOrder {

        @Test
        void picksOutTheOrderThatHasBeenCertifiedPastItsValue() {
            Subcontract small = Subcontract.build(
                    SubcontractInput.of("SC-06", "earthworks", "Dowsett Plant Hire", gbp(50_000), d("2024-11-04")));
            List<PaymentApplication> moved = APPLICATIONS.stream()
                    .map(a -> new PaymentApplication(
                            a.id(), "SC-06", a.periodNumber(), a.appliedOn(), a.gross(), a.previouslyPaid()))
                    .toList();
            List<Subcontract.Position> positions = List.of(
                    SUBCONTRACT.position(INPUTS, d("2025-03-25")),
                    small.position(INPUTS.withApplications(moved), d("2025-03-25")));
            assertThat(Subcontract.overOrder(positions).stream()
                            .map(p -> p.subcontract().id()))
                    .containsExactly("SC-06");
        }
    }

    @Nested
    class FindingASubcontract {

        @Test
        void returnsNothingWhenThereIsNoneOfThatId() {
            assertThat(Subcontract.find(List.of(SUBCONTRACT), "SC-99")).isEmpty();
        }

        @Test
        void throwsWhenTheCallerNeedsOne() {
            assertThatThrownBy(() -> Subcontract.require(List.of(SUBCONTRACT), "SC-99"))
                    .hasMessageContaining("subcontract");
            assertThat(Subcontract.require(List.of(SUBCONTRACT), "SC-01").contractor())
                    .isEqualTo("Dowsett Plant Hire");
        }
    }

    @Nested
    class Describe {

        @Test
        void saysWhereTheOrderStandsInOneLine() {
            assertThat(SUBCONTRACT.position(INPUTS, d("2025-03-25")).describe())
                    .isEqualTo("SC-01 Dowsett Plant Hire, 3 applications, inside the order");
        }
    }
}
