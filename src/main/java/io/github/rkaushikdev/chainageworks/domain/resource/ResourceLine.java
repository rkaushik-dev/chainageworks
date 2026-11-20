package io.github.rkaushikdev.chainageworks.domain.resource;

/**
 * One line of a resource return: a gang or an item of plant and how its hours were spent.
 *
 * @param resourceId the gang or the plant item, as the resource schedule names it
 * @param description what it is
 * @param count how many of them, so one line covers a gang of six
 * @param hours hours each of them spent
 * @param kind how the hours were spent
 */
public record ResourceLine(String resourceId, String description, int count, double hours, HourKind kind) {

    /** Resource hours multiplied out: six men for eight hours is forty eight. */
    public double lineHours() {
        return count * hours;
    }

    public ResourceLine withCount(int other) {
        return new ResourceLine(resourceId, description, other, hours, kind);
    }

    public ResourceLine withHours(double other) {
        return new ResourceLine(resourceId, description, count, other, kind);
    }

    public ResourceLine withKind(HourKind other) {
        return new ResourceLine(resourceId, description, count, hours, other);
    }
}
