package io.github.rkaushikdev.chainageworks.domain.itp;

/**
 * One point in an inspection and test plan.
 *
 * @param id the point's key inside its plan
 * @param name what is inspected
 * @param type what kind of gate it is
 * @param stepId the credit step this point gates
 * @param discipline who inspects it; {@code civil} when not given
 * @param reference what the inspection is against, quoted on the request form; empty when not given
 */
public record InspectionPoint(
        String id, String name, PointType type, String stepId, String discipline, String reference) {

    public InspectionPoint {
        discipline = discipline == null ? "civil" : discipline;
        reference = reference == null ? "" : reference;
    }

    /** A civil point with no reference. */
    public static InspectionPoint of(String id, String name, PointType type, String stepId) {
        return new InspectionPoint(id, name, type, stepId, null, null);
    }

    public InspectionPoint withDiscipline(String other) {
        return new InspectionPoint(id, name, type, stepId, other, reference);
    }

    public InspectionPoint withReference(String other) {
        return new InspectionPoint(id, name, type, stepId, discipline, other);
    }

    /** {@code IP-01 hold point, trench formation level and width}, like a plan row. */
    public String describe() {
        return id + " " + type.wire() + " point, " + name;
    }
}
