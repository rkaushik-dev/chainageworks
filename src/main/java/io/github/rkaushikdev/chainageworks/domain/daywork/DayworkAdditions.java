package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;

/**
 * The percentage additions the schedule puts on each head of daywork: the part that gets argued
 * over, so it is held apart from the rates and applied once, at the end.
 *
 * @param labour the addition on labour
 * @param plant the addition on plant
 * @param materials the addition on materials
 */
public record DayworkAdditions(Percent labour, Percent plant, Percent materials) {

    /** What a schedule carries unless it says otherwise. */
    public static final DayworkAdditions DEFAULT =
            new DayworkAdditions(Percent.of(88), Percent.of(12.5), Percent.of(12.5));
}
