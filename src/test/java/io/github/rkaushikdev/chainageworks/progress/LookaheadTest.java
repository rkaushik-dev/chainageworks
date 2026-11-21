package io.github.rkaushikdev.chainageworks.progress;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.samplePackages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LookaheadTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final SiteCalendar CALENDAR = SiteCalendar.build(CalendarInput.of("C1", "main works"));
    private static final List<WorkPackage> PACKAGES = samplePackages();

    private static final List<OutputAllowance> ALLOWANCES = OutputAllowance.build(List.of(
            new OutputAllowance("strip", 10), new OutputAllowance("excavate", 12), new OutputAllowance("place", 8)));

    private static LookaheadInput input() {
        return LookaheadInput.of(CONTEXT, PACKAGES, CALENDAR, ALLOWANCES, d("2025-03-26"));
    }

    private static final Lookahead LOOKAHEAD = Lookahead.build(input());

    private static Optional<StepLookahead> step(Lookahead lookahead, String packageId, String stepId) {
        return lookahead.steps().stream()
                .filter(s -> s.packageId().equals(packageId) && s.stepId().equals(stepId))
                .findFirst();
    }

    private static StepLookahead step(String packageId, String stepId) {
        return step(LOOKAHEAD, packageId, stepId).orElseThrow();
    }

    @Nested
    class BuildLookahead {

        @Test
        void runsFourWeeksFromTheDateByDefault() {
            assertThat(SiteDates.format(LOOKAHEAD.from())).isEqualTo("2025-03-26");
            assertThat(SiteDates.format(LOOKAHEAD.to())).isEqualTo("2025-04-22");
            assertThat(LOOKAHEAD.workingDays()).isEqualTo(20);
        }

        @Test
        void takesTheWindowFromTheWeeksAskedFor() {
            Lookahead shortOne = Lookahead.build(input().withWeeks(1));
            assertThat(SiteDates.format(shortOne.to())).isEqualTo("2025-04-01");
            assertThat(shortOne.workingDays()).isEqualTo(5);
        }

        @Test
        void refusesALookaheadShorterThanAWeek() {
            assertThatThrownBy(() -> Lookahead.build(input().withWeeks(0))).hasMessageContaining("at least one week");
        }

        @Test
        void refusesALookaheadWithNoGangOnIt() {
            assertThatThrownBy(() -> Lookahead.build(input().withGangs(0))).hasMessageContaining("at least one gang");
        }

        @Test
        void leavesOutAStepWithNothingLeftToBuild() {
            assertThat(step(LOOKAHEAD, "WP-01", "strip")).isEmpty();
        }

        @Test
        void measuresWhatIsLeftRatherThanWhatIsCredited() {
            StepLookahead excavate = step("WP-01", "excavate");
            assertThat(excavate.remainingMetres()).isEqualTo(200);
            assertThat(excavate.remaining().stream().map(ChainageRange::format))
                    .containsExactly("12+600.000 to 12+800.000");
        }

        @Test
        void pricesTheTimeAtTheAllowanceAndAnEightHourShift() {
            assertThat(step("WP-01", "excavate").days().getAsDouble()).isCloseTo(200.0 / 96, byLessThan(5e-7));
            assertThat(step("WP-01", "place").days().getAsDouble()).isCloseTo(400.0 / 64, byLessThan(5e-7));
        }

        @Test
        void halvesTheDaysWhenASecondGangIsPutOn() {
            Lookahead pushed = Lookahead.build(input().withGangs(2));
            double one = step("WP-01", "excavate").days().getAsDouble();
            StepLookahead two = step(pushed, "WP-01", "excavate").orElseThrow();
            assertThat(two.days().getAsDouble()).isCloseTo(one / 2, byLessThan(5e-10));
        }

        @Test
        void leavesTheDaysEmptyOnAStepWithNoAllowance() {
            assertThat(step("WP-01", "trim").days()).isEmpty();
        }

        @Test
        void addsTheDaysAcrossEveryPackage() {
            double total = LOOKAHEAD.steps().stream()
                    .mapToDouble(s -> s.days().orElse(0))
                    .sum();
            assertThat(LOOKAHEAD.daysOfWork()).isCloseTo(total, byLessThan(5e-10));
            assertThat(LOOKAHEAD.daysOfWork()).isGreaterThan(0);
        }
    }

    @Nested
    class ReadyNowAndWaitingOnInspection {

        @Test
        void namesTheStepsNothingIsInFrontOf() {
            List<StepLookahead> ready = LOOKAHEAD.readyNow();
            assertThat(ready).isNotEmpty();
            assertThat(ready).allMatch(s -> s.waitingOn().isEmpty());
        }

        @Test
        void namesTheStepAnInspectorIsHoldingRatherThanAGang() {
            List<StepLookahead> held = LOOKAHEAD.waitingOnInspection();
            assertThat(held.stream().map(s -> s.packageId() + " " + s.stepId())).contains("WP-02 excavate");
            assertThat(held).allMatch(s -> !s.heldPoints().isEmpty());
        }

        @Test
        void neverPutsAStepOnBothLists() {
            Set<String> ready = LOOKAHEAD.readyNow().stream()
                    .map(s -> s.packageId() + "/" + s.stepId())
                    .collect(Collectors.toSet());
            for (StepLookahead held : LOOKAHEAD.waitingOnInspection()) {
                assertThat(ready).doesNotContain(held.packageId() + "/" + held.stepId());
            }
        }
    }

    @Nested
    class FitsInWindow {

        @Test
        void saysSoWhenTheWorkInFrontIsMoreThanTheWindowHasRoomFor() {
            Lookahead week = Lookahead.build(input().withWeeks(1));
            assertThat(week.fitsInWindow()).isEqualTo(week.daysOfWork() <= week.workingDays());
        }

        @Test
        void fitsAWindowWithABigEnoughPushBehindIt() {
            Lookahead pushed = Lookahead.build(input().withWeeks(12).withGangs(4));
            assertThat(pushed.fitsInWindow()).isTrue();
        }
    }

    @Nested
    class FinishBy {

        @Test
        void countsTheWorkingDaysOutFromTheStartOfTheWindow() {
            LocalDate finish = LOOKAHEAD.finishBy(CALENDAR);
            assertThat(finish).isAfter(LOOKAHEAD.from());
            assertThat(SiteDates.format(finish)).startsWith("2025-");
        }
    }

    @Nested
    class Unpriced {

        @Test
        void namesTheStepsWithWorkLeftAndNoAllowance() {
            assertThat(LOOKAHEAD.unpriced()).allMatch(s -> s.days().isEmpty());
            assertThat(LOOKAHEAD.unpriced()).isNotEmpty();
        }
    }

    @Nested
    class IsInWindow {

        @Test
        void takesTheFirstAndTheLastDayOfTheWindow() {
            assertThat(LOOKAHEAD.isInWindow(d("2025-03-26"))).isTrue();
            assertThat(LOOKAHEAD.isInWindow(d("2025-04-22"))).isTrue();
            assertThat(LOOKAHEAD.isInWindow(d("2025-03-25"))).isFalse();
            assertThat(LOOKAHEAD.isInWindow(d("2025-04-23"))).isFalse();
        }
    }

    @Nested
    class DescribeLookahead {

        @Test
        void saysTheWindowTheWorkAndWhetherItFits() {
            assertThat(LOOKAHEAD.describe())
                    .containsPattern("^2025-03-26 to 2025-04-22, [\\d.]+ days of work in 20 working days, ");
        }
    }
}
