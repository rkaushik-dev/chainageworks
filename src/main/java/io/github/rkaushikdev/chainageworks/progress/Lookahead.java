package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditStep;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * What the next few weeks have to get through.
 *
 * @param from the first day of the window
 * @param to the last day of the window
 * @param workingDays working days in the window on the site calendar
 * @param steps every step with work left on it
 * @param daysOfWork total working days of work in front of the job, at the allowances
 */
public record Lookahead(LocalDate from, LocalDate to, int workingDays, List<StepLookahead> steps, double daysOfWork) {

    /** Hours to a shift, which is the shift the allowances are set on. */
    private static final int SHIFT_HOURS = 8;

    private static final int DEFAULT_WEEKS = 4;

    public Lookahead {
        steps = List.copyOf(steps);
    }

    /**
     * Build the lookahead.
     *
     * <p>Held in working days rather than calendar days, and measured against the site calendar,
     * because a lookahead that says four days over a bank holiday weekend is a lookahead nobody on
     * site will use twice.
     */
    public static Lookahead build(LookaheadInput input) {
        int weeks = input.weeks() == null ? DEFAULT_WEEKS : input.weeks();
        int gangs = input.gangs() == null ? 1 : input.gangs();
        if (weeks < 1) {
            throw new BadInputException("a lookahead runs at least one week", Details.of("weeks", weeks));
        }
        if (gangs < 1) {
            throw new BadInputException("a lookahead needs at least one gang", Details.of("gangs", gangs));
        }

        LocalDate to = SiteDates.addDays(input.from(), weeks * 7 - 1);
        Map<String, Double> allowanceFor = new HashMap<>();
        input.allowances().forEach(a -> allowanceFor.put(a.stepId(), a.metresPerHour()));
        List<StepLookahead> steps = new ArrayList<>();

        for (WorkPackage pkg : input.packages()) {
            List<StepProgressDetail> details = StepProgressDetail.forPackage(input.context(), pkg, input.from());
            CreditProfile profile = input.context().profileFor(pkg);
            Set<String> done = new HashSet<>();

            for (int index = 0; index < details.size(); index++) {
                StepProgressDetail detail = details.get(index);
                List<ChainageRange> remaining = List.of(pkg.extent());
                for (ChainageRange built : ChainageRange.union(detail.builtExtent())) {
                    remaining = remaining.stream()
                            .flatMap(part -> part.subtract(built).stream())
                            .toList();
                }
                double remainingMetres = (double) ChainageRange.coveredLength(remaining) / Stations.MM_PER_METRE;
                if (remainingMetres == 0) {
                    done.add(detail.stepId());
                    continue;
                }
                Double allowance = allowanceFor.get(detail.stepId());
                CreditStep step =
                        index < profile.steps().size() ? profile.steps().get(index) : null;
                List<String> waitingOn = step != null && step.requiresPrevious()
                        ? details.subList(0, index).stream()
                                .map(StepProgressDetail::stepId)
                                .filter(id -> !done.contains(id))
                                .toList()
                        : List.of();
                steps.add(new StepLookahead(
                        pkg.id(),
                        detail.stepId(),
                        remaining,
                        remainingMetres,
                        allowance == null
                                ? OptionalDouble.empty()
                                : OptionalDouble.of(remainingMetres / (allowance * SHIFT_HOURS * gangs)),
                        waitingOn,
                        detail.heldPoints()));
            }
        }

        double daysOfWork = 0;
        for (StepLookahead step : steps) {
            daysOfWork += step.days().orElse(0);
        }
        return new Lookahead(
                input.from(), to, input.calendar().workingDaysIn(DateRange.of(input.from(), to)), steps, daysOfWork);
    }

    /** Steps that can be worked now, because nothing is in front of them. */
    public List<StepLookahead> readyNow() {
        return steps.stream()
                .filter(step -> step.waitingOn().isEmpty() && step.heldPoints().isEmpty())
                .toList();
    }

    /** Steps waiting on an inspector rather than on a gang. */
    public List<StepLookahead> waitingOnInspection() {
        return steps.stream().filter(step -> !step.heldPoints().isEmpty()).toList();
    }

    /**
     * Whether the work in front of the job fits in the window.
     *
     * <p>Compared against the whole of what is left rather than what a lookahead would sequence,
     * because the useful answer at this stage is whether the job is going to run out of window, and
     * the sequencing only makes that worse.
     */
    public boolean fitsInWindow() {
        return daysOfWork <= workingDays;
    }

    /** The date the work in front would finish, at the allowances. */
    public LocalDate finishBy(SiteCalendar calendar) {
        return calendar.addWorkingDays(from, (int) Math.ceil(daysOfWork));
    }

    /** {@code 2025-03-26 to 2025-04-22, 9.4 days of work in 20 working days, fits the window}. */
    public String describe() {
        String fits = fitsInWindow() ? "fits the window" : "does not fit the window";
        return SiteDates.format(from) + " to " + SiteDates.format(to) + ", " + Numbers.toFixed(daysOfWork, 1)
                + " days of work in " + workingDays + " working days, " + fits;
    }

    /** Steps with work left and no allowance to price the time with. */
    public List<StepLookahead> unpriced() {
        return steps.stream().filter(step -> step.days().isEmpty()).toList();
    }

    public boolean isInWindow(LocalDate date) {
        return SiteDates.compare(date, from) >= 0 && SiteDates.compare(date, to) <= 0;
    }
}
