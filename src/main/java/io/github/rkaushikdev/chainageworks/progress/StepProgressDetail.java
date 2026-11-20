package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.credit.StepProgress;
import io.github.rkaushikdev.chainageworks.domain.itp.GateState;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.StepGate;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * How far a step has got along a package, and why it is not further.
 *
 * <p>The built extent is what the survey book says. The credited extent is what survives the hold
 * points and the open non conformances, and it is the one the money is worked out from. Reporting
 * both is the point: a step that is physically finished and entirely held reads very differently
 * from one that has not been started, and the two look identical if only the credited figure is
 * kept.
 *
 * @param stepId the step
 * @param builtExtent where the survey book says it has been built
 * @param creditedExtent what may be credited
 * @param gate whether the gates are open, part open or held
 * @param heldPoints the hold points releasing nothing
 * @param withheldExtent what the open non conformances hold back
 * @param fraction credited length over package length, as a fraction of one
 * @param builtFraction built length over package length, whatever the gates say
 */
public record StepProgressDetail(
        String stepId,
        List<ChainageRange> builtExtent,
        List<ChainageRange> creditedExtent,
        GateState gate,
        List<String> heldPoints,
        List<ChainageRange> withheldExtent,
        double fraction,
        double builtFraction) {

    public StepProgressDetail {
        builtExtent = List.copyOf(builtExtent);
        creditedExtent = List.copyOf(creditedExtent);
        heldPoints = List.copyOf(heldPoints);
        withheldExtent = List.copyOf(withheldExtent);
    }

    private static double fractionOf(WorkPackage pkg, List<ChainageRange> extent) {
        long total = pkg.length();
        long covered = ChainageRange.coveredLength(extent);
        // A point package has no length to divide by, so any record at all is the whole of it.
        // Manholes and chambers are booked this way.
        if (total == 0) {
            return covered > 0 || !extent.isEmpty() ? 1 : 0;
        }
        return Math.min(1, (double) covered / total);
    }

    /** Where the survey book says a step has been built, as at a date. */
    public static List<ChainageRange> builtExtent(
            ProgressContext context, WorkPackage pkg, String stepId, LocalDate asAt) {
        List<ChainageRange> forStep = SurveyRecord.effective(context.recordsFor(pkg.id()), asAt).stream()
                .filter(r -> r.stepId().equals(stepId))
                .map(SurveyRecord::extent)
                .toList();
        return ChainageRange.intersectLists(ChainageRange.union(forStep), List.of(pkg.extent()));
    }

    /** The hold points' say on a step, open over the whole package when no plan gates it. */
    public static StepGate gate(ProgressContext context, WorkPackage pkg, String stepId, LocalDate asAt) {
        Optional<InspectionPlan> plan = context.planFor(pkg);
        if (plan.isEmpty()) {
            return new StepGate(stepId, GateState.OPEN, List.of(pkg.extent()), List.of());
        }
        return StepGate.forStep(
                new StepGate.Query(plan.get(), context.signOffs(), stepId, pkg.id(), pkg.extent(), asAt));
    }

    /**
     * One step of one package, worked out in the order the contract works it out: what was built,
     * what the hold points released, what the open reports are still holding back, and only then
     * what may be credited.
     */
    public static StepProgressDetail forStep(ProgressContext context, WorkPackage pkg, String stepId, LocalDate asAt) {
        List<ChainageRange> built = builtExtent(context, pkg, stepId, asAt);
        List<ChainageRange> withheld =
                Ncr.withheldExtent(new Ncr.WithholdQuery(context.ncrs(), pkg.id(), stepId, asAt));
        StepGate gate = gate(context, pkg, stepId, asAt).withholding(withheld);
        List<ChainageRange> credited = ChainageRange.intersectLists(built, gate.openExtent());
        return new StepProgressDetail(
                stepId,
                built,
                credited,
                gate.state(),
                gate.heldPoints(),
                withheld,
                fractionOf(pkg, credited),
                fractionOf(pkg, built));
    }

    /** Every step of a package's rule of credit, in profile order. */
    public static List<StepProgressDetail> forPackage(ProgressContext context, WorkPackage pkg, LocalDate asAt) {
        return context.profileFor(pkg).steps().stream()
                .map(step -> forStep(context, pkg, step.id(), asAt))
                .toList();
    }

    /** The shape the rule of credit wants, taken from the credited fractions. */
    public static List<StepProgress> toStepProgress(List<StepProgressDetail> details) {
        return details.stream()
                .map(detail -> new StepProgress(detail.stepId(), toPercent(detail.fraction())))
                .toList();
    }

    /**
     * The same shape, ignoring every gate. Running the rule of credit twice, once on this and once
     * on the credited figures, is what puts a value on the work the inspections and the open
     * reports are holding up.
     */
    public static List<StepProgress> toBuiltProgress(List<StepProgressDetail> details) {
        return details.stream()
                .map(detail -> new StepProgress(detail.stepId(), toPercent(detail.builtFraction())))
                .toList();
    }

    private static Percent toPercent(double fraction) {
        return Percent.ofBasisPoints(Numbers.round(fraction * Percent.BASIS_POINTS_WHOLE));
    }

    /** What has been built but cannot be credited yet, for the held work report. */
    public List<ChainageRange> heldBackExtent() {
        List<ChainageRange> remaining = builtExtent;
        for (ChainageRange open : creditedExtent) {
            remaining =
                    remaining.stream().flatMap(r -> r.subtract(open).stream()).toList();
        }
        return ChainageRange.union(remaining);
    }
}
