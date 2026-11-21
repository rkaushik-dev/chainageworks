package io.github.rkaushikdev.chainageworks.valuation;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FinalAccountTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final LocalDate CUT_OFF = d("2025-03-31");

    private static FinalAccount.Line lineFor(String code) {
        return lineFor(code, FinalAccount.build(CONTEXT, CUT_OFF));
    }

    private static FinalAccount.Line lineFor(String code, FinalAccount account) {
        return account.lines().stream()
                .filter(l -> l.code().format().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class BuildFinalAccount {

        private static final FinalAccount ACCOUNT = FinalAccount.build(CONTEXT, CUT_OFF);

        @Test
        void givesALinePerBillItem() {
            assertThat(ACCOUNT.lines()).hasSize(8);
            assertThat(ACCOUNT.billTotal().major()).isEqualTo(312_190);
        }

        @Test
        void forecastsAnItemNobodyHasStartedAtItsBilledValue() {
            FinalAccount.Line line = lineFor("F624.1");
            assertThat(line.percentComplete()).isZero();
            assertThat(line.extrapolated()).isFalse();
            assertThat(line.forecast().major()).isEqualTo(line.billed().major());
            assertThat(line.movement().minor()).isZero();
        }

        @Test
        void reportsWhatHasBeenMeasuredAgainstEachItem() {
            FinalAccount.Line line = lineFor("E423.1");
            assertThat(line.measured().major()).isEqualTo(28_324);
        }

        @Test
        void weightsAnItemAcrossEveryPackageThatCarriesIt() {
            // WP-01 is a third through and WP-02 has not started, so the item as a whole is well short
            // of either figure.
            FinalAccount.Line line = lineFor("E423.1");
            assertThat(line.percentComplete()).isEqualTo(18.96);
            assertThat(line.extrapolated()).isFalse();
        }

        @Test
        void forecastsOnTheTrendOnceAnItemIsFarEnoughThrough() {
            FinalAccount account = FinalAccount.build(CONTEXT, CUT_OFF, Percent.of(10));
            FinalAccount.Line line = lineFor("E423.1", account);
            assertThat(line.extrapolated()).isTrue();
            assertThat(line.forecast().major()).isEqualTo(149_401.32);
        }

        @Test
        void neverForecastsBelowWhatHasAlreadyBeenMeasured() {
            for (FinalAccount.Line line : FinalAccount.build(CONTEXT, CUT_OFF).lines()) {
                assertThat(line.forecast().minor())
                        .isGreaterThanOrEqualTo(line.measured().minor());
            }
        }

        @Test
        void addsTheLinesToTheOutturnAndReportsTheMovement() {
            long sum = ACCOUNT.lines().stream()
                    .mapToLong(l -> l.forecast().minor())
                    .sum();
            assertThat(ACCOUNT.forecastOutturn().minor()).isEqualTo(sum);
            assertThat(ACCOUNT.movement().minor())
                    .isEqualTo(ACCOUNT.forecastOutturn().minor()
                            - ACCOUNT.billTotal().minor());
        }

        @Test
        void leavesAProvisionalItemOnTheBillHoweverMuchWasMeasured() {
            FinalAccount.Line line = lineFor("E423.9");
            assertThat(line.extrapolated()).isFalse();
            assertThat(line.forecast().major()).isEqualTo(14_000);
        }
    }

    @Nested
    class TheTrendThreshold {

        @Test
        void isAQuarterByDefault() {
            assertThat(FinalAccount.DEFAULT_TREND_THRESHOLD).isEqualTo(Percent.of(25));
        }

        @Test
        void stopsExtrapolatingWhenTheThresholdIsAboveTheProgress() {
            FinalAccount account = FinalAccount.build(CONTEXT, CUT_OFF, Percent.of(60));
            assertThat(lineFor("E423.1", account).extrapolated()).isFalse();
            assertThat(lineFor("E423.1", account).forecast().major()).isEqualTo(60_140);
        }

        @Test
        void startsExtrapolatingWhenTheThresholdIsLowered() {
            FinalAccount early = FinalAccount.build(CONTEXT, d("2025-03-10"), Percent.of(1));
            assertThat(lineFor("E423.1", early).extrapolated()).isTrue();
        }
    }

    @Nested
    class TheAccountMovesAsTheJobDoes {

        @Test
        void forecastsNothingMeasuredBeforeAnythingHappened() {
            FinalAccount account = FinalAccount.build(CONTEXT, d("2025-02-28"));
            assertThat(account.measuredToDate().minor()).isZero();
            assertThat(account.movement().minor()).isZero();
            assertThat(account.forecastOutturn().major()).isEqualTo(312_190);
        }

        @Test
        void doesNotMoveAtAllWhileEveryLineIsStillOnTheBill() {
            assertThat(FinalAccount.build(CONTEXT, CUT_OFF).movingLines()).isEmpty();
        }

        @Test
        void movesTheOutturnUpWhenAHeavilyMeasuredItemStartsToTrend() {
            List<SurveyRecord> records = sampleRecords().stream()
                    .map(r -> r.id().equals("SR-02") ? r.withExtent(range("12+000", "12+600")) : r)
                    .toList();
            ProgressContext heavy = sampleContext(Overrides.NONE.withRecords(records));
            FinalAccount account = FinalAccount.build(heavy, CUT_OFF, Percent.of(10));
            assertThat(account.forecastOutturn().minor())
                    .isGreaterThan(account.billTotal().minor());
        }

        @Test
        void listsTheMovingLinesWorstFirst() {
            List<FinalAccount.Line> moving =
                    FinalAccount.build(CONTEXT, CUT_OFF, Percent.of(10)).movingLines();
            assertThat(moving).isNotEmpty();
            for (int i = 1; i < moving.size(); i += 1) {
                assertThat(Math.abs(moving.get(i - 1).movement().minor()))
                        .isGreaterThanOrEqualTo(
                                Math.abs(moving.get(i).movement().minor()));
            }
        }
    }

    @Nested
    class DescribeLine {

        @Test
        void saysWhichSideOfTheThresholdALineFell() {
            FinalAccount trending = FinalAccount.build(CONTEXT, CUT_OFF, Percent.of(10));
            assertThat(lineFor("E423.1", trending).describe()).isEqualTo("E423.1 18.96% complete, on the trend");
            assertThat(lineFor("F624.1").describe()).isEqualTo("F624.1 0% complete, on the bill");
        }
    }
}
