package io.github.rkaushikdev.chainageworks.domain.credit;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A rule of credit: the steps a measured item earns through, in the order they are done.
 *
 * @param id the profile's key
 * @param name what it is called
 * @param steps the steps, in order
 */
public record CreditProfile(String id, String name, List<CreditStep> steps) {

    public CreditProfile {
        steps = List.copyOf(steps);
    }

    /**
     * Build a profile, refusing one with no steps, two steps sharing an id, a negative weight, and
     * weights that do not add to exactly 100 percent.
     */
    public static CreditProfile build(String id, String name, List<CreditStep> steps) {
        if (steps.isEmpty()) {
            throw new BadInputException("a rule of credit needs at least one step", Details.of("profile", id));
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (CreditStep step : steps) {
            if (!seen.add(step.id())) {
                throw new BadInputException(
                        "two steps in one profile share an id", Details.of("profile", id, "step", step.id()));
            }
            if (step.weight().basisPoints() < 0) {
                throw new BadInputException(
                        "a step weight cannot be negative", Details.of("profile", id, "step", step.id()));
            }
            total += step.weight().basisPoints();
        }
        if (total != Percent.BASIS_POINTS_WHOLE) {
            throw new BadInputException(
                    "the step weights of a rule of credit must add to 100 percent",
                    Details.of(
                            "profile", id, "total", Percent.ofBasisPoints(total).format(2)));
        }
        return new CreditProfile(id, name, steps);
    }

    /** Where the step sits in the profile, or -1 when it is not there. */
    public int stepIndex(String stepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(stepId)) {
                return i;
            }
        }
        return -1;
    }

    public Optional<CreditStep> findStep(String stepId) {
        return steps.stream().filter(s -> s.id().equals(stepId)).findFirst();
    }

    public CreditStep requireStep(String stepId) {
        return findStep(stepId).orElseThrow(() -> new NotFoundException("credit step", id + "/" + stepId));
    }

    /** Weight of every step up to and including the one named. */
    public Percent cumulativeWeight(String stepId) {
        int index = stepIndex(stepId);
        if (index < 0) {
            throw new NotFoundException("credit step", id + "/" + stepId);
        }
        Percent total = Percent.ZERO;
        for (CreditStep step : steps.subList(0, index + 1)) {
            total = total.plus(step.weight());
        }
        return total;
    }

    private static Percent fractionFor(List<StepProgress> progress, String stepId) {
        return progress.stream()
                .filter(p -> p.stepId().equals(stepId))
                .findFirst()
                .map(p -> p.fraction().clamp(Percent.ZERO, Percent.FULL))
                .orElse(Percent.ZERO);
    }

    /**
     * How much of an item's value the recorded progress has earned.
     *
     * <p>Steps are walked in order. A step that requires the ones before it earns nothing while any
     * of them is short, and it does not block the steps after it from being read, only from
     * counting. Each step's share is rounded to the basis point, a half going up.
     */
    public Percent earnedFraction(List<StepProgress> progress) {
        long earned = 0;
        boolean allPreviousComplete = true;
        for (CreditStep step : steps) {
            Percent fraction = fractionFor(progress, step.id());
            boolean counts = !step.requiresPrevious() || allPreviousComplete;
            if (counts) {
                earned += (long) Numbers.round(
                        (double) (step.weight().basisPoints() * fraction.basisPoints()) / Percent.BASIS_POINTS_WHOLE);
            }
            if (fraction.basisPoints() < Percent.BASIS_POINTS_WHOLE) {
                allPreviousComplete = false;
            }
        }
        return Percent.ofBasisPoints(earned);
    }

    /** Steps whose weight is being held back because an earlier step is short. */
    public List<CreditStep> blockedSteps(List<StepProgress> progress) {
        List<CreditStep> blocked = new ArrayList<>();
        boolean allPreviousComplete = true;
        for (CreditStep step : steps) {
            Percent fraction = fractionFor(progress, step.id());
            if (step.requiresPrevious() && !allPreviousComplete && fraction.basisPoints() > 0) {
                blocked.add(step);
            }
            if (fraction.basisPoints() < Percent.BASIS_POINTS_WHOLE) {
                allPreviousComplete = false;
            }
        }
        return List.copyOf(blocked);
    }

    /** The first step that is not finished, which is what a lookahead reports. */
    public Optional<CreditStep> nextStep(List<StepProgress> progress) {
        for (CreditStep step : steps) {
            if (fractionFor(progress, step.id()).basisPoints() < Percent.BASIS_POINTS_WHOLE) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    public boolean isComplete(List<StepProgress> progress) {
        return nextStep(progress).isEmpty();
    }

    /** {@code independent steps: a 40%, b 60%}. */
    public String describe() {
        return name + ": "
                + steps.stream().map(s -> s.name() + " " + s.weight().format()).collect(Collectors.joining(", "));
    }
}
