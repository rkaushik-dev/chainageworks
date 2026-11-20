package io.github.rkaushikdev.chainageworks.domain.daywork;

/**
 * An item of plant on a daywork sheet.
 *
 * @param rateId the plant line
 * @param hours normal hours worked
 * @param overtimeHours overtime hours worked
 */
public record PlantEntry(String rateId, double hours, double overtimeHours) {}
