package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.util.List;

/**
 * What a daywork schedule is built from. Any {@code null} takes the default: sterling, the
 * standard additions head by head, and time and a half for overtime.
 *
 * @param currency what the rates are in
 * @param labour the labour lines
 * @param plant the plant lines
 * @param labourAddition the addition on labour
 * @param plantAddition the addition on plant
 * @param materialsAddition the addition on materials
 * @param overtimeFactor what an hour outside normal working hours is worth, as a multiplier
 */
public record DayworkScheduleInput(
        Currency currency,
        List<DayworkRate> labour,
        List<DayworkRate> plant,
        Percent labourAddition,
        Percent plantAddition,
        Percent materialsAddition,
        Double overtimeFactor) {

    public DayworkScheduleInput {
        labour = List.copyOf(labour);
        plant = List.copyOf(plant);
    }

    /** A sterling schedule with the standard additions and overtime factor. */
    public static DayworkScheduleInput of(List<DayworkRate> labour, List<DayworkRate> plant) {
        return new DayworkScheduleInput(null, labour, plant, null, null, null, null);
    }

    public DayworkScheduleInput withCurrency(Currency other) {
        return new DayworkScheduleInput(
                other, labour, plant, labourAddition, plantAddition, materialsAddition, overtimeFactor);
    }

    public DayworkScheduleInput withLabourAddition(Percent other) {
        return new DayworkScheduleInput(
                currency, labour, plant, other, plantAddition, materialsAddition, overtimeFactor);
    }

    public DayworkScheduleInput withAdditions(DayworkAdditions other) {
        return new DayworkScheduleInput(
                currency, labour, plant, other.labour(), other.plant(), other.materials(), overtimeFactor);
    }

    public DayworkScheduleInput withOvertimeFactor(double other) {
        return new DayworkScheduleInput(
                currency, labour, plant, labourAddition, plantAddition, materialsAddition, other);
    }
}
