package io.github.rkaushikdev.chainageworks.domain.time;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What a site calendar is built from. Working days default to Monday to Friday; holidays and
 * standdowns default to none.
 *
 * @param id the calendar's key
 * @param name what people call it
 * @param workingDays ISO weekday numbers that are worked, if the contract says other than Monday
 *     to Friday
 * @param holidays days the contract does not work
 * @param standdowns working days the diary says were lost
 */
public record CalendarInput(
        String id,
        String name,
        Optional<List<Integer>> workingDays,
        List<LocalDate> holidays,
        List<Standdown> standdowns) {

    public CalendarInput {
        workingDays = workingDays == null ? Optional.empty() : workingDays.map(List::copyOf);
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
        standdowns = standdowns == null ? List.of() : List.copyOf(standdowns);
    }

    /** A Monday to Friday calendar with no holidays and no standdowns. */
    public static CalendarInput of(String id, String name) {
        return new CalendarInput(id, name, Optional.empty(), List.of(), List.of());
    }

    public CalendarInput withWorkingDays(List<Integer> days) {
        return new CalendarInput(id, name, Optional.of(days), holidays, standdowns);
    }

    public CalendarInput withHolidays(List<LocalDate> days) {
        return new CalendarInput(id, name, workingDays, days, standdowns);
    }

    public CalendarInput withStanddowns(List<Standdown> lost) {
        return new CalendarInput(id, name, workingDays, holidays, lost);
    }
}
