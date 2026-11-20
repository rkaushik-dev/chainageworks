package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Which part of a stretch one credit step may be credited over, once the hold points and the open
 * non conformances have had their say.
 *
 * @param stepId the step
 * @param state all, part or none of the stretch
 * @param openExtent stretches every hold point on the step has released
 * @param heldPoints hold points that have released nothing at all inside the extent asked about
 */
public record StepGate(String stepId, GateState state, List<ChainageRange> openExtent, List<String> heldPoints) {

    public StepGate {
        openExtent = List.copyOf(openExtent);
        heldPoints = List.copyOf(heldPoints);
    }

    /**
     * What a gate is asked for.
     *
     * @param plan the inspection plan
     * @param signOffs every sign off on the job
     * @param stepId the step
     * @param packageId the package
     * @param extent the stretch asked about
     * @param asAt sign offs after this date do not count
     */
    public record Query(
            InspectionPlan plan,
            List<SignOff> signOffs,
            String stepId,
            String packageId,
            ChainageRange extent,
            LocalDate asAt) {

        public Query {
            signOffs = List.copyOf(signOffs);
        }
    }

    /**
     * Which part of a stretch a step may be credited over.
     *
     * <p>Every hold point on the step has to have released a length before that length counts, so
     * the answer is the intersection of what each point released. A step with no hold points is
     * open over the whole extent, which is the ordinary case and has to stay cheap.
     *
     * <p>The as-at date is what makes this a valuation question rather than a site question. A
     * sign-off dated after the cut-off exists on site and does not exist in this month's
     * certificate.
     */
    public static StepGate forStep(Query query) {
        List<InspectionPoint> holds = query.plan().holdPointsForStep(query.stepId());
        if (holds.isEmpty()) {
            return new StepGate(query.stepId(), GateState.OPEN, List.of(query.extent()), List.of());
        }
        List<ChainageRange> open = List.of(query.extent());
        List<String> heldPoints = new ArrayList<>();
        for (InspectionPoint point : holds) {
            List<ChainageRange> released = releasedExtent(query, point);
            if (released.isEmpty()) {
                heldPoints.add(point.id());
            }
            open = ChainageRange.intersectLists(open, released);
        }
        long wanted = ChainageRange.coveredLength(List.of(query.extent()));
        long got = ChainageRange.coveredLength(open);
        GateState state = got == 0 ? GateState.HELD : got >= wanted ? GateState.OPEN : GateState.PART_OPEN;
        return new StepGate(query.stepId(), state, open, heldPoints);
    }

    private static List<ChainageRange> releasedExtent(Query query, InspectionPoint point) {
        List<ChainageRange> relevant = query.signOffs().stream()
                .filter(s -> s.pointId().equals(point.id())
                        && s.packageId().equals(query.packageId())
                        && s.releases()
                        && !s.signedOn().isAfter(query.asAt()))
                .map(SignOff::extent)
                .toList();
        return ChainageRange.intersectLists(ChainageRange.union(relevant), List.of(query.extent()));
    }

    /** The share of {@code extent} the step is free to be credited over, nought to one. */
    public double openFraction(ChainageRange extent) {
        long total = ChainageRange.coveredLength(List.of(extent));
        if (total == 0) {
            return state == GateState.HELD ? 0 : 1;
        }
        return (double) ChainageRange.coveredLength(openExtent) / total;
    }

    /** This gate narrowed by whatever the open non conformances are holding back. */
    public StepGate withholding(List<ChainageRange> withheld) {
        if (withheld.isEmpty()) {
            return this;
        }
        List<ChainageRange> open = openExtent;
        for (ChainageRange range : withheld) {
            open = open.stream().flatMap(r -> r.subtract(range).stream()).toList();
        }
        List<ChainageRange> merged = ChainageRange.union(open);
        long remaining = ChainageRange.coveredLength(merged);
        long before = ChainageRange.coveredLength(openExtent);
        GateState narrowed = remaining == 0 ? GateState.HELD : remaining >= before ? state : GateState.PART_OPEN;
        return new StepGate(stepId, narrowed, merged, heldPoints);
    }
}
