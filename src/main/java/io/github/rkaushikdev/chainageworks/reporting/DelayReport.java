package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.delay.BarReason;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayCause;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.delay.Entitlement;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The extension of time position at a date.
 *
 * <p>The report is built around the gap between what was claimed and what was awarded, because that
 * gap is what the two sides are actually arguing about and it is invisible on a programme.
 *
 * @param asAt the date the position is taken at
 * @param lines one line per event that had started by then, in the order they started
 * @param position where completion stands
 * @param daysLate calendar days past the extended completion date
 * @param damages liquidated damages to date
 * @param damagesCapped whether the damages have run into the contract cap
 */
public record DelayReport(
        LocalDate asAt,
        List<DelayLine> lines,
        CompletionTerms.Position position,
        int daysLate,
        Money damages,
        boolean damagesCapped) {

    public DelayReport {
        lines = List.copyOf(lines);
    }

    /**
     * One delay event as the report shows it.
     *
     * @param id the event's key
     * @param reference the register reference
     * @param cause why the works stopped
     * @param entitlement what that cause entitles the contractor to
     * @param from the first day of the event
     * @param to the last day of the event
     * @param workingDays working days the event ran for, which is what was claimed
     * @param awardedDays working days awarded; empty while it is unassessed
     * @param bar why the event cannot carry an award, empty when it can
     * @param carriesMoney whether the event carries money as well as time
     */
    public record DelayLine(
            String id,
            String reference,
            DelayCause cause,
            Entitlement entitlement,
            LocalDate from,
            LocalDate to,
            int workingDays,
            Optional<Integer> awardedDays,
            Optional<BarReason> bar,
            boolean carriesMoney) {

        public DelayLine {
            awardedDays = awardedDays == null ? Optional.empty() : awardedDays;
            bar = bar == null ? Optional.empty() : bar;
        }
    }

    /**
     * The position at {@code asAt} under {@code terms}. A contract that fixes no completion date
     * has no extension of time position, so a caller holding optional terms maps over them.
     */
    public static DelayReport build(
            CompletionTerms terms, List<DelayEvent> events, SiteCalendar calendar, Money contractSum, LocalDate asAt) {
        CompletionTerms.Position position = terms.position(events, calendar, asAt);
        List<DelayLine> lines = DelayEvent.eventsTo(events, asAt).stream()
                .map(event -> new DelayLine(
                        event.id(),
                        event.reference(),
                        event.cause(),
                        event.cause().entitlement(),
                        event.period().from(),
                        event.period().to(),
                        calendar.workingDaysIn(event.period()),
                        event.awardedDays(),
                        event.barReason(terms.noticeDays()),
                        event.carriesMoney(terms.noticeDays())))
                .toList();
        return new DelayReport(
                asAt,
                lines,
                position,
                position.daysLate(asAt),
                terms.liquidatedDamages(position, contractSum, asAt),
                terms.isDamagesCapped(position, contractSum, asAt));
    }

    /** Events that would have bought time and did not, because of the paperwork. */
    public List<DelayLine> barredLines() {
        return lines.stream()
                .filter(line -> line.bar().isPresent() && line.bar().get() != BarReason.NO_ENTITLEMENT)
                .toList();
    }

    /** Events still waiting on an assessment. */
    public List<DelayLine> unassessedLines() {
        return lines.stream()
                .filter(line -> line.bar().isEmpty() && line.awardedDays().isEmpty())
                .toList();
    }

    /** Working days claimed across every event. */
    public int claimedDays() {
        return lines.stream().mapToInt(DelayLine::workingDays).sum();
    }

    /** The report as plain text: the position, the events, and a note once damages reach the cap. */
    public String render() {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("extension of time position at " + SiteDates.format(asAt)));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("completion", SiteDates.format(position.baseCompletion())),
                Map.entry("extended to", SiteDates.format(position.extendedCompletion())),
                Map.entry("days claimed", String.valueOf(claimedDays())),
                Map.entry("days awarded", String.valueOf(position.awardedDays())),
                Map.entry("days barred", String.valueOf(position.barredDays())),
                Map.entry("days late", String.valueOf(daysLate)),
                Map.entry("damages", damages.format()))));

        if (!lines.isEmpty()) {
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("event"),
                            Column.of("cause"),
                            Column.of("from"),
                            Column.of("to"),
                            Column.right("days"),
                            Column.right("awarded"),
                            Column.of("state")),
                    lines.stream()
                            .map(line -> List.of(
                                    line.reference(),
                                    line.cause().wire(),
                                    SiteDates.format(line.from()),
                                    SiteDates.format(line.to()),
                                    String.valueOf(line.workingDays()),
                                    line.awardedDays().map(String::valueOf).orElse(""),
                                    line.bar()
                                            .map(bar -> bar.wire().replace('_', ' '))
                                            .orElse("claimable")))
                            .toList()));
        }

        if (damagesCapped) {
            sections.add("");
            sections.add("damages have reached the contract cap");
        }
        return String.join("\n", sections);
    }

    /** Nothing in damages, in the currency of the contract sum. */
    public static Money zeroDamages(Money contractSum) {
        return Money.zero(contractSum.currency());
    }
}
