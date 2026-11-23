package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.util.List;

/**
 * What a daywork schedule is built from. Any {@code null} takes the default: sterling, the
 * standard additions head by head, time and a half for overtime, standing plant at half rate, and
 * no deadline for handing sheets in.
 *
 * @param currency what the rates are in
 * @param labour the labour lines
 * @param plant the plant lines
 * @param labourAddition the addition on labour
 * @param plantAddition the addition on plant
 * @param materialsAddition the addition on materials
 * @param overtimeFactor what an hour outside normal working hours is worth, as a multiplier
 * @param standingFactor what an hour of plant standing idle is worth, as a share of its rate
 * @param submissionDays calendar days after the work within which a sheet has to be handed in
 */
public record DayworkScheduleInput(
        Currency currency,
        List<DayworkRate> labour,
        List<DayworkRate> plant,
        Percent labourAddition,
        Percent plantAddition,
        Percent materialsAddition,
        Double overtimeFactor,
        Double standingFactor,
        Integer submissionDays) {

    public DayworkScheduleInput {
        labour = List.copyOf(labour);
        plant = List.copyOf(plant);
    }

    /** A sterling schedule with the standard additions and overtime factor. */
    public static DayworkScheduleInput of(List<DayworkRate> labour, List<DayworkRate> plant) {
        return new DayworkScheduleInput(null, labour, plant, null, null, null, null, null, null);
    }

    public DayworkScheduleInput withCurrency(Currency other) {
        Draft draft = new Draft(this);
        draft.currency = other;
        return draft.input();
    }

    public DayworkScheduleInput withLabourAddition(Percent other) {
        Draft draft = new Draft(this);
        draft.labourAddition = other;
        return draft.input();
    }

    public DayworkScheduleInput withOvertimeFactor(double other) {
        Draft draft = new Draft(this);
        draft.overtimeFactor = other;
        return draft.input();
    }

    public DayworkScheduleInput withStandingFactor(double other) {
        Draft draft = new Draft(this);
        draft.standingFactor = other;
        return draft.input();
    }

    public DayworkScheduleInput withSubmissionDays(int other) {
        Draft draft = new Draft(this);
        draft.submissionDays = other;
        return draft.input();
    }

    public DayworkScheduleInput withAdditions(DayworkAdditions other) {
        Draft draft = new Draft(this);
        draft.labourAddition = other.labour();
        draft.plantAddition = other.plant();
        draft.materialsAddition = other.materials();
        return draft.input();
    }

    /** A copy of the input with some fields changed, so each wither names only its own fields. */
    private static final class Draft {
        private Currency currency;
        private List<DayworkRate> labour;
        private List<DayworkRate> plant;
        private Percent labourAddition;
        private Percent plantAddition;
        private Percent materialsAddition;
        private Double overtimeFactor;
        private Double standingFactor;
        private Integer submissionDays;

        private Draft(DayworkScheduleInput input) {
            currency = input.currency;
            labour = input.labour;
            plant = input.plant;
            labourAddition = input.labourAddition;
            plantAddition = input.plantAddition;
            materialsAddition = input.materialsAddition;
            overtimeFactor = input.overtimeFactor;
            standingFactor = input.standingFactor;
            submissionDays = input.submissionDays;
        }

        private DayworkScheduleInput input() {
            return new DayworkScheduleInput(
                    currency,
                    labour,
                    plant,
                    labourAddition,
                    plantAddition,
                    materialsAddition,
                    overtimeFactor,
                    standingFactor,
                    submissionDays);
        }
    }
}
