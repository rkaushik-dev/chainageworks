package io.github.rkaushikdev.chainageworks.valuation;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CertificateTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final ProgressContext CONTEXT = sampleContext();
    private static final Money CONTRACT_SUM = gbp(312_190);

    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));

    private static final ValuationPeriod MARCH = SCHEDULE.byNumber(3);

    private static final CertificateInput BASE = CertificateInput.of(CONTEXT, CONTRACT_SUM, RetentionTerms.DEFAULT);

    private static final Certificate CERTIFICATE = Certificate.build(BASE, MARCH, Money.zero(Currency.GBP));

    @Nested
    class BuildCertificate {

        @Test
        void valuesTheMeasuredWorkAtTheCutOff() {
            assertThat(CERTIFICATE.measuredWork().major()).isEqualTo(16_152.5);
        }

        @Test
        void namesTheBillRevisionItWasPricedAgainst() {
            assertThat(CERTIFICATE.billRevision()).isEqualTo("Rev A");
        }

        @Test
        void carriesNothingForMaterialsWhenNoneWereDeclared() {
            assertThat(CERTIFICATE.materialsOnSite().minor()).isZero();
            assertThat(CERTIFICATE.materialsClaimed()).isEmpty();
        }

        @Test
        void holdsRetentionOffTheGross() {
            assertThat(CERTIFICATE.gross().major()).isEqualTo(16_152.5);
            assertThat(CERTIFICATE.retention().held().major()).isEqualTo(807.63);
            assertThat(CERTIFICATE.netValuation().major()).isEqualTo(15_344.87);
        }

        @Test
        void paysTheWholeNetFigureOnTheFirstCertificate() {
            assertThat(CERTIFICATE.amountDue().major()).isEqualTo(15_344.87);
            assertThat(CERTIFICATE.isNegative()).isFalse();
        }

        @Test
        void carriesThePackageDetailThroughForTheBackingSheets() {
            assertThat(CERTIFICATE.earnings().stream().map(PackageEarning::packageId))
                    .containsExactly("WP-01", "WP-03", "WP-02");
        }

        @Test
        void refusesAPreviousCertificateInAnotherCurrency() {
            assertThatThrownBy(() -> Certificate.build(BASE, MARCH, Money.zero(Currency.EUR)))
                    .hasMessageContaining("another currency");
        }
    }

    @Nested
    class MaterialsOnTheCertificate {

        private static MaterialsInput fill() {
            return MaterialsInput.of(
                            "MOS-01",
                            "WP-02",
                            ItemCode.parse("E423.1"),
                            "imported fill stockpiled at the compound",
                            Quantity.of(1200, Unit.M3),
                            gbp(9000),
                            d("2025-03-10"))
                    .withVestedOn(d("2025-03-12"));
        }

        private static final MaterialsOnSite PIPES = MaterialsOnSite.build(fill());

        @Test
        void addsTheAllowedValueToTheGross() {
            Certificate withMaterials =
                    Certificate.build(BASE.withMaterials(List.of(PIPES)), MARCH, Money.zero(Currency.GBP));
            assertThat(withMaterials.materialsOnSite().major()).isEqualTo(8100);
            assertThat(withMaterials.gross().major()).isEqualTo(16_152.5 + 8100);
            assertThat(withMaterials.materialsClaimed().stream().map(MaterialsOnSite::id))
                    .containsExactly("MOS-01");
        }

        @Test
        void honoursADifferentAllowance() {
            Certificate full = Certificate.build(
                    BASE.withMaterials(List.of(PIPES)).withMaterialsAllowance(Percent.of(100)),
                    MARCH,
                    Money.zero(Currency.GBP));
            assertThat(full.materialsOnSite().major()).isEqualTo(9000);
        }

        @Test
        void dropsThemOnceTheyAreBuiltIn() {
            MaterialsOnSite built = MaterialsOnSite.build(fill().withIncorporatedOn(d("2025-03-20")));
            Certificate after = Certificate.build(BASE.withMaterials(List.of(built)), MARCH, Money.zero(Currency.GBP));
            assertThat(after.materialsOnSite().minor()).isZero();
        }
    }

    @Nested
    class LiquidatedDamagesOnTheCertificate {

        private static final CertificateInput LATE =
                BASE.withCompletion(CompletionTerms.build(d("2025-03-14"), gbp(100)), List.of());

        @Test
        void deductsTheDamagesToDateFromTheNetFigure() {
            Certificate late = Certificate.build(LATE, MARCH, Money.zero(Currency.GBP));
            assertThat(late.damages().major()).isEqualTo(1100);
            assertThat(late.gross().major()).isEqualTo(16_152.5);
            assertThat(late.netValuation().major()).isEqualTo(15_344.87 - 1100);
            assertThat(late.lines()).contains(new Certificate.Line("less liquidated damages", gbp(1100), false));
            assertThat(late.notes()).contains("liquidated damages are being deducted for late completion");
        }

        @Test
        void leavesTheLineOffAJobThatIsNotLate() {
            assertThat(CERTIFICATE.damages().minor()).isZero();
            assertThat(CERTIFICATE.lines())
                    .extracting(Certificate.Line::name)
                    .doesNotContain("less liquidated damages");
            Certificate early = Certificate.build(
                    BASE.withCompletion(CompletionTerms.build(d("2025-06-30"), gbp(100)), List.of()),
                    MARCH,
                    Money.zero(Currency.GBP));
            assertThat(early.damages().minor()).isZero();
        }

        @Test
        void stopsDeductingOnTheDayTheWorksWereTakenOver() {
            Certificate takenOver = Certificate.build(
                    LATE.withSubstantialCompletionOn(d("2025-03-20")), MARCH, Money.zero(Currency.GBP));
            assertThat(takenOver.damages().major()).isEqualTo(600);
        }

        @Test
        void paysOnlyTheDamagesThatAccruedSinceTheLastCertificate() {
            List<Certificate> series = Certificate.series(SCHEDULE.periods().subList(0, 4), LATE);
            Certificate march = series.get(2);
            Certificate april = series.get(3);
            assertThat(april.damages().minus(march.damages()).major()).isEqualTo(3100);
            assertThat(april.previouslyCertified()).isEqualTo(march.netValuation());
            assertThat(april.amountDue()).isEqualTo(april.netValuation().minus(march.netValuation()));
        }
    }

    @Nested
    class TheMinimumCertificate {

        private List<Certificate> seriesWithMinimum(double minimum) {
            return Certificate.series(
                    SCHEDULE.periods().subList(0, 4), BASE.withInterimTerms(InterimTerms.withMinimum(gbp(minimum))));
        }

        @Test
        void holdsAMonthBelowTheMinimumOverAndPaysNothing() {
            Certificate march = seriesWithMinimum(20_000).get(2);
            assertThat(march.heldOver()).isTrue();
            assertThat(march.netValuation().major()).isEqualTo(15_344.87);
            assertThat(march.amountDue().minor()).isZero();
            assertThat(march.certifiedToDate().minor()).isZero();
            assertThat(march.lines())
                    .contains(new Certificate.Line("held over below the minimum certificate", gbp(15_344.87), false));
            assertThat(march.notes())
                    .contains("the movement is below the minimum certificate and is held over to next month");
        }

        @Test
        void carriesTheHeldMovementIntoTheNextMonth() {
            Certificate april = seriesWithMinimum(20_000).get(3);
            assertThat(april.previouslyCertified().minor()).isZero();
            assertThat(april.heldOver()).isEqualTo(april.netValuation().major() < 20_000);
            assertThat(april.amountDue()).isEqualTo(april.heldOver() ? gbp(0) : april.netValuation());
        }

        @Test
        void certifiesAMonthThatClearsTheMinimum() {
            List<Certificate> series = seriesWithMinimum(10_000);
            assertThat(series.get(2).heldOver()).isFalse();
            assertThat(series.get(2).amountDue().major()).isEqualTo(15_344.87);
            assertThat(series.get(3).previouslyCertified().major()).isEqualTo(15_344.87);
        }

        @Test
        void leavesAnUnrestrictedCertificateAlone() {
            assertThat(CERTIFICATE.heldOver()).isFalse();
            assertThat(CERTIFICATE.lines())
                    .extracting(Certificate.Line::name)
                    .doesNotContain("held over below the minimum certificate");
        }
    }

    @Nested
    class AnAdvancePayment {

        @Test
        void addsTheAdvanceAndDeductsNothingBelowTheThreshold() {
            Certificate march = Certificate.build(
                    BASE.withInterimTerms(InterimTerms.NONE.withAdvance(AdvancePayment.of(gbp(5000), d("2025-02-01")))),
                    MARCH,
                    Money.zero(Currency.GBP));
            assertThat(march.advancePaid()).isEqualTo(gbp(5000));
            assertThat(march.advanceRepaid().minor()).isZero();
            assertThat(march.netValuation().major()).isEqualTo(20_344.87);
            assertThat(march.lines())
                    .contains(
                            new Certificate.Line("advance payment", gbp(5000), false),
                            new Certificate.Line("less advance repaid", gbp(0), false));
        }

        @Test
        void takesTheAdvanceBackOnceTheGrossPassesTheThreshold() {
            AdvancePayment quick = AdvancePayment.build(gbp(5000), d("2025-02-01"), Percent.of(0), Percent.of(100));
            Certificate march = Certificate.build(
                    BASE.withInterimTerms(InterimTerms.NONE.withAdvance(quick)), MARCH, Money.zero(Currency.GBP));
            assertThat(march.advanceRepaid()).isEqualTo(gbp(5000));
            assertThat(march.netValuation().major()).isEqualTo(15_344.87);
            assertThat(march.notes()).contains("the advance payment has been repaid in full");
        }

        @Test
        void leavesTheLinesOffUntilTheAdvanceIsPaid() {
            Certificate march = Certificate.build(
                    BASE.withInterimTerms(InterimTerms.NONE.withAdvance(AdvancePayment.of(gbp(5000), d("2025-04-01")))),
                    MARCH,
                    Money.zero(Currency.GBP));
            assertThat(march.advancePaid().minor()).isZero();
            assertThat(march.lines()).extracting(Certificate.Line::name).doesNotContain("advance payment");
            assertThat(march.netValuation()).isEqualTo(CERTIFICATE.netValuation());
        }
    }

    @Nested
    class TheAmountDueIsADifference {

        @Test
        void paysOnlyTheMovementWhenWorkWasCertifiedBefore() {
            Certificate second = Certificate.build(BASE, MARCH, gbp(10_000));
            assertThat(second.amountDue().major()).isEqualTo(5344.87);
            assertThat(second.certifiedToDate().major()).isEqualTo(15_344.87);
        }

        @Test
        void goesNegativeWhenThisMonthValuesLessThanTheLast() {
            Certificate overpaid = Certificate.build(BASE, MARCH, gbp(20_000));
            assertThat(overpaid.isNegative()).isTrue();
            assertThat(overpaid.amountDue().major()).isEqualTo(-4655.13);
        }
    }

    @Nested
    class CertifySeries {

        private static final List<Certificate> SERIES =
                Certificate.series(SCHEDULE.periods().subList(0, 4), BASE);

        @Test
        void runsOneCertificatePerPeriod() {
            assertThat(SERIES.stream().map(c -> c.period().number())).containsExactly(1, 2, 3, 4);
        }

        @Test
        void feedsEachOneTheLastNetFigure() {
            assertThat(SERIES.get(0).previouslyCertified().minor()).isZero();
            assertThat(SERIES.get(3).previouslyCertified())
                    .isEqualTo(SERIES.get(2).netValuation());
        }

        @Test
        void paysNothingInTheMonthsBeforeAnythingWasBuilt() {
            assertThat(SERIES.get(0).amountDue().minor()).isZero();
            assertThat(SERIES.get(1).amountDue().minor()).isZero();
        }

        @Test
        void paysTheMovementInTheMonthTheWorkLanded() {
            assertThat(SERIES.get(2).amountDue().major()).isEqualTo(15_344.87);
        }

        @Test
        void addsUpToTheLastNetValuation() {
            long paid = SERIES.stream().mapToLong(c -> c.amountDue().minor()).sum();
            assertThat(paid).isEqualTo(SERIES.get(3).netValuation().minor());
        }
    }
}
