package io.github.rkaushikdev.chainageworks.invariants;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.haulingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.samplePackages;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Diagnosis;
import io.github.rkaushikdev.chainageworks.app.Doctor;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.earthworks.Movement;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.progress.LookaheadInput;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.progress.StepLookahead;
import io.github.rkaushikdev.chainageworks.progress.StepProgressDetail;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.SeededRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Properties of the lookahead, the haul plan and the job doctor that hold from any start and on any sites. */
class PlanningInvariantsTest {

    private static final ProgressContext CONTEXT = sampleContext();

    private static final SiteCalendar CALENDAR = SiteCalendar.build(CalendarInput.of("C1", "main works"));

    private static final List<WorkPackage> PACKAGES = samplePackages();

    private static final List<OutputAllowance> ALLOWANCES =
            OutputAllowance.build(List.of(new OutputAllowance("strip", 10), new OutputAllowance("excavate", 12)));

    private static final List<LocalDate> STARTS = IntStream.range(0, 120)
            .mapToObj(i -> SiteDates.addDays(d("2025-01-01"), i * 3))
            .toList();

    private static LookaheadInput lookaheadFrom(LocalDate from) {
        return LookaheadInput.of(CONTEXT, PACKAGES, CALENDAR, ALLOWANCES, from);
    }

    private static WorkPackage packageOf(StepLookahead step) {
        return PACKAGES.stream()
                .filter(p -> p.id().equals(step.packageId()))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> keys(List<StepLookahead> steps) {
        return steps.stream().map(s -> s.packageId() + "/" + s.stepId()).collect(Collectors.toSet());
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Quantity cubicMetres(double amount) {
        return Quantity.of(amount, Unit.M3);
    }

    @Nested
    class ALookaheadNeverAsksForWorkThatHasAlreadyBeenBuilt {

        @Test
        void holdsFromEveryStartDateAcrossTheJob() {
            for (LocalDate from : STARTS) {
                Lookahead lookahead = Lookahead.build(lookaheadFrom(from));
                for (StepLookahead step : lookahead.steps()) {
                    WorkPackage pkg = packageOf(step);
                    StepProgressDetail built = StepProgressDetail.forPackage(CONTEXT, pkg, from).stream()
                            .filter(detail -> detail.stepId().equals(step.stepId()))
                            .findFirst()
                            .orElseThrow();
                    List<ChainageRange> overlap = ChainageRange.intersectLists(step.remaining(), built.builtExtent());
                    assertThat(ChainageRange.coveredLength(overlap)).isZero();
                }
            }
        }

        @Test
        void neverAsksForMoreThanThePackageIsLong() {
            for (LocalDate from : STARTS) {
                Lookahead lookahead = Lookahead.build(lookaheadFrom(from));
                for (StepLookahead step : lookahead.steps()) {
                    WorkPackage pkg = packageOf(step);
                    double length = (double) (pkg.extent().to() - pkg.extent().from()) / Stations.MM_PER_METRE;
                    assertThat(step.remainingMetres()).isLessThanOrEqualTo(length + 1e-9);
                    assertThat(step.remainingMetres()).isGreaterThan(0);
                }
            }
        }
    }

    @Nested
    class ALookaheadSplitsEveryStepExactlyOnce {

        @Test
        void neverPutsAStepOnBothTheReadyListAndTheHeldList() {
            for (LocalDate from : STARTS) {
                Lookahead lookahead = Lookahead.build(lookaheadFrom(from));
                Set<String> ready = keys(lookahead.readyNow());
                Set<String> held = keys(lookahead.waitingOnInspection());
                for (String key : held) {
                    assertThat(ready.contains(key)).isFalse();
                }
            }
        }

        @Test
        void shrinksTheWorkInFrontAsTheJobGoesOn() {
            Lookahead early = Lookahead.build(lookaheadFrom(d("2025-01-01")).withWeeks(52));
            Lookahead late = Lookahead.build(lookaheadFrom(d("2025-04-01")).withWeeks(52));
            assertThat(late.daysOfWork()).isLessThanOrEqualTo(early.daysOfWork());
        }
    }

    @Nested
    class AHaulPlanNeverMovesMoreThanThereIsOrMoreThanFits {

        @Test
        void holdsAcrossRandomSitesAndLinks() {
            SeededRandom random = new SeededRandom(31415);
            for (int i = 0; i < 200; i += 1) {
                int cut = random.between(0, 20_000);
                int borrow = random.between(0, 10_000);
                int fill = random.between(0, 25_000);
                int tip = random.between(0, 15_000);
                int borrowCost = random.between(1, 6);
                int tipCost = random.between(3, 12);
                int cutToFill = random.between(1, 10);
                int cutToTip = random.between(1, 10);
                int borrowToFill = random.between(1, 10);

                HaulAllocation allocation = HaulAllocation.allocate(new HaulAllocation.Input(
                        List.of(
                                new HaulSite("CUT", "cut", SiteKind.CUT, cubicMetres(cut), gbp(0)),
                                new HaulSite("BOR", "borrow", SiteKind.BORROW, cubicMetres(borrow), gbp(borrowCost))),
                        List.of(
                                new HaulSite("FIL", "fill", SiteKind.FILL, cubicMetres(fill), gbp(0)),
                                new HaulSite("TIP", "tip", SiteKind.TIP, cubicMetres(tip), gbp(tipCost))),
                        List.of(
                                new HaulLink("CUT", "FIL", cutToFill),
                                new HaulLink("CUT", "TIP", cutToTip),
                                new HaulLink("BOR", "FIL", borrowToFill)),
                        gbp(1)));

                double outOfCut = allocation.movements().stream()
                        .filter(m -> m.fromId().equals("CUT"))
                        .map(Movement::volume)
                        .mapToDouble(Quantity::amount)
                        .sum();
                double intoFill = allocation.movements().stream()
                        .filter(m -> m.toId().equals("FIL"))
                        .map(Movement::volume)
                        .mapToDouble(Quantity::amount)
                        .sum();

                assertThat(outOfCut).isLessThanOrEqualTo(cut);
                assertThat(intoFill).isLessThanOrEqualTo(fill);
                assertThat(allocation.cost().minor()).isGreaterThanOrEqualTo(0);
                assertThat(allocation.haulMoment()).isGreaterThanOrEqualTo(0);
                assertThat(allocation.unplaced().amount()).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        void isBalancedOnlyWhenNothingIsLeftAtEitherEnd() {
            HaulAllocation balanced = HaulAllocation.allocate(new HaulAllocation.Input(
                    List.of(new HaulSite("CUT", "cut", SiteKind.CUT, cubicMetres(5000), gbp(0))),
                    List.of(new HaulSite("FIL", "fill", SiteKind.FILL, cubicMetres(5000), gbp(0))),
                    List.of(new HaulLink("CUT", "FIL", 2)),
                    gbp(1)));
            assertThat(balanced.isBalanced()).isTrue();
        }
    }

    @Nested
    class TheDoctorNeverReportsAnErrorOnAJobTheToolBuiltItself {

        @Test
        void holdsForEveryFixture() {
            for (Job job : List.of(sampleJob(), commercialJob(), haulingJob())) {
                Diagnosis diagnosis = Doctor.diagnose(job);
                assertThat(diagnosis.errors()).as(job.settings().id()).isZero();
                assertThat(diagnosis.isHealthy()).as(job.settings().id()).isTrue();
            }
        }

        @Test
        void countsEveryFindingAsOneLevelOrTheOther() {
            Diagnosis diagnosis = Doctor.diagnose(haulingJob());
            assertThat(diagnosis.errors() + diagnosis.warnings())
                    .isEqualTo(diagnosis.findings().size());
        }
    }
}
