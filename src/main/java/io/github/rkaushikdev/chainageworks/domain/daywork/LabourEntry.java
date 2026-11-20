package io.github.rkaushikdev.chainageworks.domain.daywork;

/**
 * A gang on a daywork sheet.
 *
 * @param rateId the labour grade
 * @param people how many of them
 * @param hours normal hours each worked
 * @param overtimeHours overtime hours each worked
 */
public record LabourEntry(String rateId, int people, double hours, double overtimeHours) {}
