package io.github.rkaushikdev.chainageworks.domain.delay;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Something that held the works up, the notice given of it, and the extension of time awarded.
 *
 * @param id the event's key
 * @param reference the early warning or notice reference
 * @param cause why the works stopped
 * @param description what happened
 * @param period the days it ran for
 * @param extent where on the alignment, when the event has a place
 * @param packageIds the packages it held up
 * @param notifiedOn when the contractor gave notice; absent means they have not
 * @param awardedDays working days awarded by the engineer; absent while it is unassessed
 */
public record DelayEvent(
        String id,
        String reference,
        DelayCause cause,
        String description,
        DateRange period,
        Optional<ChainageRange> extent,
        List<String> packageIds,
        Optional<LocalDate> notifiedOn,
        Optional<Integer> awardedDays) {

    /** Days the contract allows for giving notice before a claim is time barred. */
    public static final int DEFAULT_NOTICE_DAYS = 21;

    public DelayEvent {
        extent = extent == null ? Optional.empty() : extent;
        packageIds = List.copyOf(packageIds);
        notifiedOn = notifiedOn == null ? Optional.empty() : notifiedOn;
        awardedDays = awardedDays == null ? Optional.empty() : awardedDays;
    }

    /**
     * Build an event, refusing a blank description, a period that runs backwards, notice before
     * the event started and a negative award.
     */
    public static DelayEvent build(DelayEventInput input) {
        if (Text.trim(input.description()).isEmpty()) {
            throw new BadInputException("a delay event needs a description", Details.of("event", input.id()));
        }
        DateRange period = DateRange.of(input.from(), input.to());
        Optional<LocalDate> notifiedOn = Optional.ofNullable(input.notifiedOn());
        if (notifiedOn.isPresent() && notifiedOn.get().isBefore(input.from())) {
            throw new BadInputException(
                    "notice cannot be given before the event started",
                    Details.of(
                            "event", input.id(),
                            "started", SiteDates.format(input.from()),
                            "notified", SiteDates.format(notifiedOn.get())));
        }
        Optional<Integer> awardedDays = Optional.ofNullable(input.awardedDays());
        if (awardedDays.isPresent() && awardedDays.get() < 0) {
            throw new BadInputException(
                    "an award is a whole number of working days",
                    Details.of("event", input.id(), "awardedDays", awardedDays.get()));
        }
        return new DelayEvent(
                input.id(),
                input.reference(),
                input.cause(),
                Text.trim(input.description()),
                period,
                Optional.ofNullable(input.extent()),
                input.packageIds(),
                notifiedOn,
                awardedDays);
    }

    /**
     * Why the event cannot carry an award, or empty when it can.
     *
     * <p>The time bar is the harshest rule on a civil contract and the one people are most
     * surprised by: an event that genuinely delayed the works buys nothing at all if notice went
     * in a day late. Reporting it as a reason rather than silently awarding nothing is the
     * difference between a contractor who can argue and one who does not know what happened.
     */
    public Optional<BarReason> barReason(int noticeDays) {
        if (cause.entitlement() == Entitlement.NONE) {
            return Optional.of(BarReason.NO_ENTITLEMENT);
        }
        if (notifiedOn.isEmpty()) {
            return Optional.of(BarReason.NOT_NOTIFIED);
        }
        if (SiteDates.diffDays(period.from(), notifiedOn.get()) > noticeDays) {
            return Optional.of(BarReason.NOTIFIED_LATE);
        }
        return Optional.empty();
    }

    /** {@link #barReason(int)} under the standard notice period. */
    public Optional<BarReason> barReason() {
        return barReason(DEFAULT_NOTICE_DAYS);
    }

    public boolean isClaimable(int noticeDays) {
        return barReason(noticeDays).isEmpty();
    }

    public boolean isClaimable() {
        return isClaimable(DEFAULT_NOTICE_DAYS);
    }

    public boolean carriesMoney(int noticeDays) {
        return isClaimable(noticeDays) && cause.entitlement() == Entitlement.TIME_AND_MONEY;
    }

    public boolean carriesMoney() {
        return carriesMoney(DEFAULT_NOTICE_DAYS);
    }

    /** Calendar days the event ran for, whether or not anybody was working. */
    public int elapsedDays() {
        return period.days();
    }

    /** Working days lost, which is what an award is measured in. */
    public int workingDaysLost(SiteCalendar calendar) {
        return calendar.workingDaysIn(period);
    }

    /**
     * The award, or what the event is worth if it has not been assessed yet.
     *
     * <p>An unassessed event is worth the working days it ran for, which is the contractor's own
     * case rather than the engineer's. Reporting that separately from an award keeps the forecast
     * honest about what is still to be argued.
     */
    public int assessedDays(SiteCalendar calendar, int noticeDays) {
        if (!isClaimable(noticeDays)) {
            return 0;
        }
        return awardedDays.orElseGet(() -> workingDaysLost(calendar));
    }

    public int assessedDays(SiteCalendar calendar) {
        return assessedDays(calendar, DEFAULT_NOTICE_DAYS);
    }

    public boolean isAssessed() {
        return awardedDays.isPresent();
    }

    /** Events that had started by a date, in the order they started. */
    public static List<DelayEvent> eventsTo(List<DelayEvent> events, LocalDate asAt) {
        List<DelayEvent> out = new ArrayList<>(
                events.stream().filter(e -> !e.period().from().isAfter(asAt)).toList());
        out.sort((a, b) -> {
            int byStart = a.period().from().compareTo(b.period().from());
            if (byStart != 0) {
                return byStart;
            }
            return a.id().compareTo(b.id()) < 0 ? -1 : 1;
        });
        return List.copyOf(out);
    }

    /** {@code EW/2025/003 utilities, 2025-03-03 to 2025-03-14, claimable}, like a register line. */
    public String describe() {
        String state = barReason().map(bar -> bar.wire().replace('_', ' ')).orElse("claimable");
        return reference + " " + cause.wire() + ", " + SiteDates.format(period.from()) + " to "
                + SiteDates.format(period.to()) + ", " + state;
    }
}
