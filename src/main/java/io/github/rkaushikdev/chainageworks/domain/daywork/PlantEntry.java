package io.github.rkaushikdev.chainageworks.domain.daywork;

/**
 * An item of plant on a daywork sheet.
 *
 * <p>Standing time is the hours a machine was kept on the job for the instructed work but stood
 * idle, waiting on a survey or an access, say. It is paid, because the contractor could not hire
 * the machine out elsewhere, but at the schedule's reduced standing rate rather than the working
 * rate, because nobody was running it.
 *
 * @param rateId the plant line
 * @param hours normal hours worked
 * @param overtimeHours overtime hours worked
 * @param standingHours hours the plant stood idle on the work
 */
public record PlantEntry(String rateId, double hours, double overtimeHours, double standingHours) {

    /** Plant that worked every hour it was booked. */
    public PlantEntry(String rateId, double hours, double overtimeHours) {
        this(rateId, hours, overtimeHours, 0);
    }
}
