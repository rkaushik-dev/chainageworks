package io.github.rkaushikdev.chainageworks.domain.time;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The days a contract works: which weekdays, which holidays it does not, and which working days
 * the diary says were stood down.
 *
 * @param id the calendar's key
 * @param name what people call it
 * @param workingDays ISO weekday numbers that are worked, Monday being 1, sorted and distinct
 * @param holidays days not worked, sorted and distinct
 * @param standdowns lost working days, one per date, sorted
 */
public record SiteCalendar(
        String id, String name, List<Integer> workingDays, List<LocalDate> holidays, List<Standdown> standdowns) {

    /** Monday to Friday, which is what a contract says unless it says otherwise. */
    public static final List<Integer> DEFAULT_WORKING_DAYS = List.of(1, 2, 3, 4, 5);

    private static final int SEARCH_LIMIT = 400;

    public SiteCalendar {
        workingDays = List.copyOf(workingDays);
        holidays = List.copyOf(holidays);
        standdowns = List.copyOf(standdowns);
    }

    /** Build a calendar, refusing a weekday that is not 1 to 7 and a calendar that never works. */
    public static SiteCalendar build(CalendarInput input) {
        List<Integer> requested = input.workingDays().orElse(DEFAULT_WORKING_DAYS);
        for (Integer day : requested) {
            if (day == null || day < 1 || day > 7) {
                throw new BadInputException("a working day is an iso weekday from 1 to 7", Details.of("day", day));
            }
        }
        List<Integer> workingDays = requested.stream().distinct().sorted().toList();
        if (workingDays.isEmpty()) {
            throw new BadInputException(
                    "a calendar has to work at least one day a week", Details.of("calendar", input.id()));
        }
        List<LocalDate> holidays = input.holidays().stream().distinct().sorted().toList();
        return new SiteCalendar(input.id(), input.name(), workingDays, holidays, dedupe(input.standdowns()));
    }

    /** One standdown per day. A later entry for the same day replaces an earlier one. */
    private static List<Standdown> dedupe(List<Standdown> standdowns) {
        Map<LocalDate, Standdown> byDate = new LinkedHashMap<>();
        for (Standdown standdown : standdowns) {
            byDate.put(standdown.date(), standdown);
        }
        List<Standdown> out = new ArrayList<>(byDate.values());
        out.sort(Comparator.comparing(Standdown::date));
        return out;
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    /** A day the contract expects work on, holidays already taken out. */
    public boolean isWorkingDay(LocalDate date) {
        return workingDays.contains(SiteDates.dayOfWeek(date)) && !isHoliday(date);
    }

    /** The standdown booked on {@code date}, if any. */
    public Optional<Standdown> standdownOn(LocalDate date) {
        return standdowns.stream().filter(s -> s.date().equals(date)).findFirst();
    }

    /** A working day that was not stood down: the only kind that builds anything. */
    public boolean isProductiveDay(LocalDate date) {
        return isWorkingDay(date) && standdownOn(date).isEmpty();
    }

    public int workingDaysIn(DateRange range) {
        return (int) range.eachDate().filter(this::isWorkingDay).count();
    }

    public int productiveDaysIn(DateRange range) {
        return (int) range.eachDate().filter(this::isProductiveDay).count();
    }

    /**
     * Standdowns that actually cost something. A standdown booked on a Sunday on a five day
     * calendar is a diary note, not a lost day, and counting it would inflate every delay claim
     * built on this.
     */
    public List<Standdown> standdownsIn(DateRange range) {
        return standdowns.stream()
                .filter(s -> range.contains(s.date()) && isWorkingDay(s.date()))
                .toList();
    }

    public int standdownDaysIn(DateRange range) {
        return standdownsIn(range).size();
    }

    public LocalDate nextWorkingDay(LocalDate date) {
        return step(date, 1);
    }

    public LocalDate previousWorkingDay(LocalDate date) {
        return step(date, -1);
    }

    private LocalDate step(LocalDate date, int direction) {
        LocalDate cursor = date.plusDays(direction);
        for (int guard = 0; guard < SEARCH_LIMIT; guard += 1) {
            if (isWorkingDay(cursor)) {
                return cursor;
            }
            cursor = cursor.plusDays(direction);
        }
        throw new BadInputException(
                "no working day found within a year", Details.of("calendar", id, "from", SiteDates.format(date)));
    }

    /** Step whole working days. Zero returns the day itself, worked or not. */
    public LocalDate addWorkingDays(LocalDate date, int days) {
        LocalDate cursor = date;
        for (int i = 0; i < Math.abs(days); i += 1) {
            cursor = days >= 0 ? nextWorkingDay(cursor) : previousWorkingDay(cursor);
        }
        return cursor;
    }

    /** Working days from one date to another, counting neither end. */
    public int workingDaysBetween(LocalDate from, LocalDate to) {
        if (SiteDates.compare(from, to) > 0) {
            return -workingDaysBetween(to, from);
        }
        int count = 0;
        for (LocalDate cursor = from.plusDays(1); SiteDates.compare(cursor, to) < 0; cursor = cursor.plusDays(1)) {
            if (isWorkingDay(cursor)) {
                count += 1;
            }
        }
        return count;
    }
}
