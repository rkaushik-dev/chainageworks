package io.github.rkaushikdev.chainageworks.valuation;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexReading;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The fluctuation clause as it lands on an interim certificate. */
class CertificateAdjustmentTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final ProgressContext CONTEXT = sampleContext();
    private static final Money CONTRACT_SUM = gbp(312_190);

    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));

    private static final ValuationPeriod MARCH = SCHEDULE.byNumber(3);

    private static final IndexSeries LABOUR = IndexSeries.build(
            "IDX-LAB",
            "civil engineering labour",
            List.of(
                    IndexReading.firm(2024, 10, 100),
                    IndexReading.firm(2025, 1, 105),
                    IndexReading.firm(2025, 2, 110),
                    new IndexReading(2025, 3, 120, true)));

    private static final AdjustmentTerms TERMS =
            AdjustmentTerms.build(d("2024-10-25"), Percent.of(10), List.of(new Weighting("IDX-LAB", Percent.of(90))));

    private static final CertificateInput BASE = CertificateInput.of(CONTEXT, CONTRACT_SUM, RetentionTerms.DEFAULT);

    private static final Certificate PLAIN = Certificate.build(BASE, MARCH, Money.zero(Currency.GBP));

    private static final Certificate ADJUSTED =
            Certificate.build(BASE.withAdjustment(TERMS, List.of(LABOUR)), MARCH, Money.zero(Currency.GBP));

    @Nested
    class ACertificateWithNoFluctuationClause {

        @Test
        void reportsAnAdjustmentOfNothing() {
            assertThat(PLAIN.priceAdjustment().amount().minor()).isZero();
            assertThat(PLAIN.priceAdjustment().factor().lines()).isEmpty();
        }

        @Test
        void restsOnNoIndexAtAll() {
            assertThat(PLAIN.restsOnProvisionalIndex()).isFalse();
        }

        @Test
        void grossesUpToTheMeasuredWorkOnItsOwn() {
            assertThat(PLAIN.gross().major()).isEqualTo(16_152.5);
        }
    }

    @Nested
    class ACertificateWithAFluctuationClause {

        @Test
        void adjustsThePermanentWorkByTheFactor() {
            // 20 percent movement, weighted 90 percent, on 90 percent of the work.
            assertThat(ADJUSTED.priceAdjustment().factor().factor()).isCloseTo(0.18, byLessThan(5e-11));
            assertThat(ADJUSTED.priceAdjustment().adjustable().major()).isCloseTo(14_537.25, byLessThan(0.005));
            assertThat(ADJUSTED.priceAdjustment().amount().major()).isCloseTo(2616.71, byLessThan(0.005));
        }

        @Test
        void carriesTheAdjustmentIntoTheGross() {
            assertThat(ADJUSTED.gross().major()).isCloseTo(18_769.21, byLessThan(0.005));
            assertThat(ADJUSTED.measuredWork().major()).isEqualTo(16_152.5);
        }

        @Test
        void holdsRetentionOnTheAdjustedGrossRatherThanTheMeasuredWork() {
            assertThat(ADJUSTED.retention().held().major())
                    .isGreaterThan(PLAIN.retention().held().major());
        }

        @Test
        void saysTheCertificateRestsOnAProvisionalFigure() {
            assertThat(ADJUSTED.restsOnProvisionalIndex()).isTrue();
            assertThat(ADJUSTED.priceAdjustment().provisional()).isTrue();
        }

        @Test
        void leavesMaterialsOnSiteOutOfTheAdjustment() {
            MaterialsOnSite capping = MaterialsOnSite.build(MaterialsInput.of(
                            "MOS-01",
                            "WP-01",
                            ItemCode.parse("E423.1"),
                            "imported capping stockpiled at the compound",
                            Quantity.of(400, Unit.M3),
                            gbp(8000),
                            d("2025-03-05"))
                    .withVestedOn(d("2025-03-05")));
            Certificate withMaterials = Certificate.build(
                    BASE.withAdjustment(TERMS, List.of(LABOUR)).withMaterials(List.of(capping)),
                    MARCH,
                    Money.zero(Currency.GBP));
            // The standard ninety percent allowance on materials on site.
            assertThat(withMaterials.materialsOnSite().major()).isEqualTo(7200);
            assertThat(withMaterials.priceAdjustment().amount().major())
                    .isEqualTo(ADJUSTED.priceAdjustment().amount().major());
            assertThat(withMaterials.gross().major()).isCloseTo(ADJUSTED.gross().major() + 7200, byLessThan(0.005));
        }
    }

    @Nested
    class AFallingIndex {

        private static final IndexSeries FALLING = IndexSeries.build(
                "IDX-LAB", null, List.of(IndexReading.firm(2024, 10, 100), IndexReading.firm(2025, 3, 90)));

        @Test
        void takesMoneyOffTheCertificate() {
            Certificate down =
                    Certificate.build(BASE.withAdjustment(TERMS, List.of(FALLING)), MARCH, Money.zero(Currency.GBP));
            assertThat(down.priceAdjustment().amount().minor()).isNegative();
            assertThat(down.gross().major()).isLessThan(PLAIN.gross().major());
        }
    }

    @Nested
    class ACertificateOnALateJob {

        private Certificate lateBy(String due, IndexSeries series) {
            return Certificate.build(
                    BASE.withAdjustment(TERMS, List.of(series))
                            .withCompletion(CompletionTerms.build(d(due), gbp(0)), List.of()),
                    MARCH,
                    Money.zero(Currency.GBP));
        }

        @Test
        void holdsTheFactorAtTheCompletionDateWhenTheIndicesHaveRisenSince() {
            Certificate late = lateBy("2025-02-14", LABOUR);
            assertThat(late.priceAdjustment().heldAtCompletion()).isTrue();
            assertThat(late.priceAdjustment().factor().factor()).isCloseTo(0.09, byLessThan(5e-11));
            assertThat(late.priceAdjustment().amount().minor())
                    .isLessThan(ADJUSTED.priceAdjustment().amount().minor());
            assertThat(late.notes())
                    .contains("the price adjustment is held at the completion date because the works are late");
        }

        @Test
        void letsAFallSinceTheCompletionDateThrough() {
            IndexSeries falling = IndexSeries.build(
                    "IDX-LAB",
                    null,
                    List.of(
                            IndexReading.firm(2024, 10, 100),
                            IndexReading.firm(2025, 2, 115),
                            IndexReading.firm(2025, 3, 104)));
            Certificate late = lateBy("2025-02-14", falling);
            assertThat(late.priceAdjustment().heldAtCompletion()).isFalse();
            assertThat(late.priceAdjustment().factor().factor()).isCloseTo(0.9 * 0.04, byLessThan(5e-11));
        }

        @Test
        void usesTheCurrentFiguresWhileTheJobIsInsideItsTime() {
            Certificate onTime = lateBy("2025-06-30", LABOUR);
            assertThat(onTime.priceAdjustment().heldAtCompletion()).isFalse();
            assertThat(onTime.priceAdjustment().amount())
                    .isEqualTo(ADJUSTED.priceAdjustment().amount());
            assertThat(ADJUSTED.priceAdjustment().heldAtCompletion()).isFalse();
        }
    }

    @Nested
    class ASeriesWithAFluctuationClause {

        private static final List<Certificate> SERIES =
                Certificate.series(SCHEDULE.periods().subList(0, 4), BASE.withAdjustment(TERMS, List.of(LABOUR)));

        @Test
        void adjustsEveryCertificateInTheSeries() {
            assertThat(SERIES).hasSize(4);
            assertThat(SERIES.get(0).priceAdjustment().factor().factor()).isCloseTo(0.045, byLessThan(5e-11));
            assertThat(SERIES.get(2).priceAdjustment().factor().factor()).isCloseTo(0.18, byLessThan(5e-11));
        }

        @Test
        void namesTheCertificatesThatWillHaveToBeReworked() {
            assertThat(Certificate.provisional(SERIES).stream()
                            .map(c -> c.period().number()))
                    .containsExactly(3, 4);
        }

        @Test
        void stillPaysOnlyTheMovementEachMonth() {
            double paid =
                    SERIES.stream().mapToDouble(c -> c.amountDue().major()).reduce(0, Double::sum);
            double cumulative = SERIES.get(SERIES.size() - 1).netValuation().major();
            assertThat(paid).isCloseTo(cumulative, byLessThan(0.005));
        }
    }
}
