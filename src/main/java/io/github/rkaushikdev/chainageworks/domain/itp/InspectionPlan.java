package io.github.rkaushikdev.chainageworks.domain.itp;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * An inspection and test plan: the points that gate the steps of one rule of credit.
 *
 * @param id the plan's key
 * @param name what it is called
 * @param profileId the rule of credit whose steps these points gate
 * @param points the points, in plan order
 */
public record InspectionPlan(String id, String name, String profileId, List<InspectionPoint> points) {

    public InspectionPlan {
        points = List.copyOf(points);
    }

    /**
     * Build a plan, checked against the rule of credit it gates, because a point hung on a step
     * that does not exist gates nothing and nobody finds out until the work it was meant to stop
     * has been buried.
     */
    public static InspectionPlan build(
            String id, String name, String profileId, List<InspectionPoint> points, CreditProfile profile) {
        if (!profile.id().equals(profileId)) {
            throw new BadInputException(
                    "the plan names a different rule of credit",
                    Details.of("plan", id, "planProfile", profileId, "profile", profile.id()));
        }
        Set<String> seen = new HashSet<>();
        for (InspectionPoint point : points) {
            if (!seen.add(point.id())) {
                throw new BadInputException(
                        "two inspection points share an id", Details.of("plan", id, "point", point.id()));
            }
            if (profile.findStep(point.stepId()).isEmpty()) {
                throw new BadInputException(
                        "an inspection point gates a step the profile does not have",
                        Details.of("plan", id, "point", point.id(), "step", point.stepId()));
            }
        }
        return new InspectionPlan(id, name, profileId, points);
    }

    public Optional<InspectionPoint> findPoint(String pointId) {
        return points.stream().filter(p -> p.id().equals(pointId)).findFirst();
    }

    public InspectionPoint requirePoint(String pointId) {
        return findPoint(pointId).orElseThrow(() -> new NotFoundException("inspection point", id + "/" + pointId));
    }

    public List<InspectionPoint> pointsForStep(String stepId) {
        return points.stream().filter(p -> p.stepId().equals(stepId)).toList();
    }

    /** Only these stop credit. Witness and review points never do. */
    public List<InspectionPoint> holdPointsForStep(String stepId) {
        return pointsForStep(stepId).stream()
                .filter(p -> p.type() == PointType.HOLD)
                .toList();
    }

    public List<InspectionPoint> witnessPointsForStep(String stepId) {
        return pointsForStep(stepId).stream()
                .filter(p -> p.type() == PointType.WITNESS)
                .toList();
    }

    /** The steps a hold point stands on, in plan order. */
    public List<String> gatedSteps() {
        Set<String> steps = new LinkedHashSet<>();
        points.stream().filter(p -> p.type() == PointType.HOLD).forEach(p -> steps.add(p.stepId()));
        return List.copyOf(steps);
    }

    /** The disciplines involved, sorted. */
    public List<String> disciplines() {
        return List.copyOf(
                new TreeSet<>(points.stream().map(InspectionPoint::discipline).toList()));
    }
}
