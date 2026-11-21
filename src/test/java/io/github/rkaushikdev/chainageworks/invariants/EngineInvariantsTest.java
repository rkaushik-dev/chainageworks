package io.github.rkaushikdev.chainageworks.invariants;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleRecords;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.signOff;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.progress.StepProgressDetail;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import io.github.rkaushikdev.chainageworks.support.SeededRandom;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.CertificateInput;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.RetentionTerms;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Properties of the progress engine and the certificate that hold at every cut-off across the sample job. */
class EngineInvariantsTest {

    private static final ProgressContext CONTEXT = sampleContext();

    private static final Money CONTRACT_SUM = Money.fromMajor(312_190, Currency.GBP);

    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 12, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));

    private static final List<LocalDate> CUT_OFFS = IntStream.range(0, 400)
            .mapToObj(i -> SiteDates.addDays(d("2025-01-01"), i))
            .toList();

    private static long totalEarned(ProgressContext context, LocalDate asAt) {
        return PackageEarning.earnAll(context, asAt).stream()
                .mapToLong(earning -> earning.earnedValue().minor())
                .sum();
    }

    private static List<Long> earnedValues(ProgressContext context, LocalDate asAt) {
        return PackageEarning.earnAll(context, asAt).stream()
                .map(earning -> earning.earnedValue().minor())
                .toList();
    }

    @Nested
    class WhatIsCreditedIsASubsetOfWhatIsBuilt {

        @Test
        void holdsAtEveryCutOffAcrossTheJob() {
            for (WorkPackage pkg : CONTEXT.register().packages()) {
                for (LocalDate asAt : CUT_OFFS) {
                    for (StepProgressDetail step : StepProgressDetail.forPackage(CONTEXT, pkg, asAt)) {
                        List<ChainageRange> outside =
                                ChainageRange.intersectLists(step.creditedExtent(), step.builtExtent());
                        assertThat(ChainageRange.coveredLength(outside))
                                .isEqualTo(ChainageRange.coveredLength(step.creditedExtent()));
                        assertThat(step.fraction()).isLessThanOrEqualTo(step.builtFraction() + 1e-9);
                    }
                }
            }
        }
    }

    @Nested
    class APackageNeverEarnsMoreThanItsBudget {

        @Test
        void holdsAtEveryCutOff() {
            for (WorkPackage pkg : CONTEXT.register().packages()) {
                for (LocalDate asAt : CUT_OFFS) {
                    PackageEarning earning = PackageEarning.earn(CONTEXT, pkg, asAt);
                    assertThat(earning.earnedValue().minor())
                            .isLessThanOrEqualTo(earning.budget().minor());
                    assertThat(earning.earnedValue().minor()).isGreaterThanOrEqualTo(0);
                    assertThat(earning.fraction().value()).isGreaterThanOrEqualTo(0);
                    assertThat(earning.fraction().value()).isLessThanOrEqualTo(100);
                }
            }
        }
    }

    @Nested
    class NothingIsEarnedThatWasNotMeasured {

        @Test
        void holdsForEveryMeasurableAllocationAtEveryCutOff() {
            for (WorkPackage pkg : CONTEXT.register().packages()) {
                for (LocalDate asAt : CUT_OFFS) {
                    for (PackageEarning.Allocation allocation :
                            PackageEarning.earn(CONTEXT, pkg, asAt).allocations()) {
                        if (allocation.notEarnable()) {
                            assertThat(allocation.earnedValue().minor()).isZero();
                            continue;
                        }
                        assertThat(allocation.earnedQuantity().milli())
                                .isLessThanOrEqualTo(allocation.measured().milli());
                    }
                }
            }
        }
    }

    @Nested
    class TheEarnedFigureOnlyEverRisesAsTheCutOffMovesOut {

        @Test
        void holdsAcrossTheWholeJobBecauseNothingInTheSampleIsEverUndone() {
            long previous = -1;
            for (LocalDate asAt : CUT_OFFS) {
                long total = totalEarned(CONTEXT, asAt);
                assertThat(total).isGreaterThanOrEqualTo(previous);
                previous = total;
            }
        }

        @Test
        void fallsOnlyWhenAReportIsRaisedWhichIsTheOneThingThatUndoesCredit() {
            ProgressContext noReports = sampleContext(Overrides.NONE.withNcrs(List.of()));
            long previous = -1;
            for (LocalDate asAt : CUT_OFFS) {
                long total = totalEarned(noReports, asAt);
                assertThat(total).isGreaterThanOrEqualTo(previous);
                previous = total;
            }
        }
    }

    @Nested
    class TheCertificateAlwaysAddsUp {

        @Test
        void grossLessRetentionIsTheNetAndTheMovementIsTheAmountDue() {
            Money previous = Money.zero(Currency.GBP);
            CertificateInput input = CertificateInput.of(CONTEXT, CONTRACT_SUM, RetentionTerms.DEFAULT);
            for (ValuationPeriod period : SCHEDULE.periods()) {
                Certificate certificate = Certificate.build(input, period, previous);
                assertThat(certificate.gross().minor())
                        .isEqualTo(certificate.measuredWork().minor()
                                + certificate.materialsOnSite().minor());
                assertThat(certificate.netValuation().minor())
                        .isEqualTo(certificate.gross().minor()
                                - certificate.retention().held().minor());
                assertThat(certificate.amountDue().minor())
                        .isEqualTo(certificate.netValuation().minor() - previous.minor());
                previous = certificate.netValuation();
            }
        }
    }

    @Nested
    class TheAnswerDoesNotDependOnTheOrderTheRecordsArrivedIn {

        @Test
        void holdsForAHundredShufflesOfTheSurveyBook() {
            SeededRandom random = new SeededRandom(4_912_337);
            LocalDate asAt = d("2025-03-31");
            List<Long> expected = earnedValues(CONTEXT, asAt);
            for (int run = 0; run < 100; run += 1) {
                List<SurveyRecord> shuffled = new ArrayList<>(sampleRecords());
                for (int i = shuffled.size() - 1; i > 0; i -= 1) {
                    int j = random.between(0, i);
                    Collections.swap(shuffled, i, j);
                }
                ProgressContext reordered = sampleContext(Overrides.NONE.withRecords(shuffled));
                assertThat(earnedValues(reordered, asAt)).isEqualTo(expected);
            }
        }

        @Test
        void holdsForTheSignOffRegisterToo() {
            LocalDate asAt = d("2025-03-31");
            List<Long> expected = earnedValues(CONTEXT, asAt);
            List<SignOff> reversedSignOffs = new ArrayList<>(sampleContext().signOffs());
            Collections.reverse(reversedSignOffs);
            ProgressContext reversed = sampleContext(Overrides.NONE.withSignOffs(reversedSignOffs));
            assertThat(earnedValues(reversed, asAt)).isEqualTo(expected);
        }
    }

    @Nested
    class ASignOffNeverReducesWhatIsEarned {

        @Test
        void holdsWhenOneMoreStretchIsReleased() {
            LocalDate asAt = d("2025-04-30");
            long before = totalEarned(CONTEXT, asAt);
            List<SignOff> more = new ArrayList<>(sampleContext().signOffs());
            more.add(signOff("SO-90", "IP-E1", "WP-01", "12+400", "12+800", "2025-04-04"));
            long after = totalEarned(sampleContext(Overrides.NONE.withSignOffs(more)), asAt);
            assertThat(after).isGreaterThanOrEqualTo(before);
        }

        @Test
        void holdsWhenAWiderStretchIsReleasedOnTheSamePoint() {
            LocalDate asAt = d("2025-03-31");
            long before = totalEarned(CONTEXT, asAt);
            List<SignOff> widened = sampleContext().signOffs().stream()
                    .map(s -> new SignOff(
                            s.id(),
                            s.pointId(),
                            s.packageId(),
                            range("12+000", "12+800"),
                            s.signedOn(),
                            s.by(),
                            s.outcome(),
                            s.note()))
                    .toList();
            ProgressContext wider = sampleContext(Overrides.NONE.withSignOffs(widened));
            long after = totalEarned(wider, asAt);
            assertThat(after).isGreaterThanOrEqualTo(before);
        }
    }
}
