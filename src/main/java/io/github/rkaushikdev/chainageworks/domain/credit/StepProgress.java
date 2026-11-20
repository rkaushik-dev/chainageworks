package io.github.rkaushikdev.chainageworks.domain.credit;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;

/**
 * How far through one step of a rule of credit the work is.
 *
 * @param stepId the step
 * @param fraction how much of it is done
 */
public record StepProgress(String stepId, Percent fraction) {}
