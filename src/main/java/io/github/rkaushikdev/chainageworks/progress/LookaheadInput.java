package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import java.time.LocalDate;
import java.util.List;

/**
 * What a lookahead is built from.
 *
 * @param context the progress context
 * @param packages the packages to look ahead on
 * @param calendar the site calendar the window is counted on
 * @param allowances the output allowances the time is priced with
 * @param gangs gangs available for a step, so a two gang push halves the days; null means one
 * @param from the first day of the window
 * @param weeks how many weeks the window runs; null means four
 */
public record LookaheadInput(
        ProgressContext context,
        List<WorkPackage> packages,
        SiteCalendar calendar,
        List<OutputAllowance> allowances,
        Integer gangs,
        LocalDate from,
        Integer weeks) {

    public LookaheadInput {
        packages = List.copyOf(packages);
        allowances = List.copyOf(allowances);
    }

    public static LookaheadInput of(
            ProgressContext context,
            List<WorkPackage> packages,
            SiteCalendar calendar,
            List<OutputAllowance> allowances,
            LocalDate from) {
        return new LookaheadInput(context, packages, calendar, allowances, null, from, null);
    }

    public LookaheadInput withGangs(int other) {
        return new LookaheadInput(context, packages, calendar, allowances, other, from, weeks);
    }

    public LookaheadInput withWeeks(int other) {
        return new LookaheadInput(context, packages, calendar, allowances, gangs, from, other);
    }
}
