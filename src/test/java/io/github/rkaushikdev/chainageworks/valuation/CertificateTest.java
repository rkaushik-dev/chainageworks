package io.github.rkaushikdev.chainageworks.valuation;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
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
