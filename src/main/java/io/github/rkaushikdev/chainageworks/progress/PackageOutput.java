package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * What a package built set against the hours it took, step by step.
 *
 * @param packageId the package
 * @param asAt the cut off
 * @param steps one line per step of the rule of credit
 * @param unallocatedHours hours booked to the package without naming one of its steps
 * @param labourHours every labour hour booked to the package
 * @param plantHours every plant hour booked to the package
 */
public record PackageOutput(
        String packageId,
        LocalDate asAt,
        List<StepOutput> steps,
        double unallocatedHours,
        double labourHours,
        double plantHours) {

    public PackageOutput {
        steps = List.copyOf(steps);
    }

    /**
     * One step's output.
     *
     * @param stepId the step
     * @param builtMetres metres of the package built by the cut-off, whatever the gates say
     * @param labourHours labour hours booked to the step
     * @param plantHours plant hours booked to the step
     * @param lostHours standing and idle hours booked to the step
     * @param achieved metres per labour hour actually achieved, absent when no hours were booked
     * @param allowed metres per labour hour allowed, absent when no allowance is set
     * @param ratio achieved over allowed; above one is ahead of the allowance
     */
    public record StepOutput(
            String stepId,
            double builtMetres,
            double labourHours,
            double plantHours,
            double lostHours,
            OptionalDouble achieved,
            OptionalDouble allowed,
            OptionalDouble ratio) {

        /** {@code strip, 8.33 metres an hour, 83 percent of the allowance}. */
        public String describe() {
            if (achieved.isEmpty()) {
                return stepId + ", no hours booked";
            }
            String against = ratio.isEmpty()
                    ? "no allowance set"
                    : Numbers.toFixed(ratio.getAsDouble() * 100, 0) + " percent of the allowance";
            return stepId + ", " + Numbers.toFixed(achieved.getAsDouble(), 2) + " metres an hour, " + against;
        }
    }

    /** {@link #of(ProgressContext, WorkPackage, List, LocalDate, List)} with no allowances. */
    public static PackageOutput of(
            ProgressContext context, WorkPackage pkg, List<ResourceReturn> returns, LocalDate asAt) {
        return of(context, pkg, returns, asAt, List.of());
    }

    /**
     * Set what was built against the hours it took.
     *
     * <p>Built extent is used rather than credited extent, and that is the whole point of the
     * report. A gang that laid four hundred metres of pipe was productive whether or not the hold
     * point has been signed, and charging the inspector's diary against the ganger's output would
     * make the number useless for the only thing it is for, which is planning next week.
     */
    public static PackageOutput of(
            ProgressContext context,
            WorkPackage pkg,
            List<ResourceReturn> returns,
            LocalDate asAt,
            List<OutputAllowance> allowances) {
        List<ResourceReturn> mine = ResourceReturn.returnsTo(ResourceReturn.returnsFor(returns, pkg.id()), asAt);
        Map<String, Double> allowanceFor = new HashMap<>();
        allowances.forEach(a -> allowanceFor.put(a.stepId(), a.metresPerHour()));

        List<StepOutput> steps = new ArrayList<>();
        for (StepProgressDetail detail : StepProgressDetail.forPackage(context, pkg, asAt)) {
            List<ResourceReturn> onStep = mine.stream()
                    .filter(r -> r.stepId().equals(Optional.of(detail.stepId())))
                    .toList();
            List<ResourceLine> labour = lines(onStep, true);
            List<ResourceLine> plant = lines(onStep, false);
            List<ResourceLine> both = new ArrayList<>(labour);
            both.addAll(plant);
            double labourHours = ResourceReturn.totalHours(labour);
            double builtMetres = (double) ChainageRange.coveredLength(detail.builtExtent()) / Stations.MM_PER_METRE;
            OptionalDouble achieved =
                    labourHours == 0 ? OptionalDouble.empty() : OptionalDouble.of(builtMetres / labourHours);
            Double allowance = allowanceFor.get(detail.stepId());
            OptionalDouble allowed = allowance == null ? OptionalDouble.empty() : OptionalDouble.of(allowance);
            OptionalDouble ratio = achieved.isEmpty() || allowed.isEmpty()
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(achieved.getAsDouble() / allowed.getAsDouble());
            steps.add(new StepOutput(
                    detail.stepId(),
                    builtMetres,
                    labourHours,
                    ResourceReturn.totalHours(plant),
                    ResourceReturn.hoursOfKind(both, HourKind.STANDING)
                            + ResourceReturn.hoursOfKind(both, HourKind.IDLE),
                    achieved,
                    allowed,
                    ratio));
        }

        Set<String> stepIds = new HashSet<>();
        context.profileFor(pkg).steps().forEach(s -> stepIds.add(s.id()));
        List<ResourceReturn> loose = mine.stream()
                .filter(r -> r.stepId().map(id -> !stepIds.contains(id)).orElse(true))
                .toList();
        List<ResourceLine> looseLines = new ArrayList<>(lines(loose, true));
        looseLines.addAll(lines(loose, false));
        return new PackageOutput(
                pkg.id(),
                asAt,
                steps,
                ResourceReturn.totalHours(looseLines),
                ResourceReturn.totalHours(lines(mine, true)),
                ResourceReturn.totalHours(lines(mine, false)));
    }

    private static List<ResourceLine> lines(List<ResourceReturn> returns, boolean labour) {
        return returns.stream()
                .flatMap(r -> (labour ? r.labour() : r.plant()).stream())
                .toList();
    }

    /** Steps running behind the allowance, worst first. */
    public List<StepOutput> behindAllowance() {
        List<StepOutput> behind = new ArrayList<>(steps.stream()
                .filter(step -> step.ratio().isPresent() && step.ratio().getAsDouble() < 1)
                .toList());
        behind.sort(Comparator.comparingDouble(step -> step.ratio().orElse(0)));
        return List.copyOf(behind);
    }

    /** Steps with hours booked against them and nothing built to show for it. */
    public List<StepOutput> nothingToShow() {
        return steps.stream()
                .filter(step -> step.labourHours() > 0 && step.builtMetres() == 0)
                .toList();
    }
}
