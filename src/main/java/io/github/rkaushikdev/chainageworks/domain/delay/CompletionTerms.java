package io.github.rkaushikdev.chainageworks.domain.delay;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The completion terms of the contract.
 *
 * <p>Damages are per calendar day late, because the employer's loss does not stop at the weekend.
 * Extensions of time are in working days, because the contractor's loss does. The two units are
 * genuinely different and running them together is the classic way an extension comes out a week
 * short.
 *
 * @param baseCompletion the completion date in the contract
 * @param damagesPerDay liquidated damages per calendar day late
 * @param damagesCap cap on the total damages, as a share of the contract sum
 * @param noticeDays days allowed for notice before a claim is time barred
 */
public record CompletionTerms(
        LocalDate baseCompletion, Money damagesPerDay, Optional<Percent> damagesCap, int noticeDays) {

    public CompletionTerms {
        damagesCap = damagesCap == null ? Optional.empty() : damagesCap;
    }

    /**
     * One event the time bar shut out.
     *
     * @param event the event
     * @param reason why it is barred
     * @param days the working days it cost
     */
    public record Barred(DelayEvent event, BarReason reason, int days) {}

    /**
     * Where completion stands at a date.
     *
     * @param baseCompletion the completion date in the contract
     * @param extendedCompletion the date once the awards are added
     * @param awardedDays working days granted by the events that were properly notified
     * @param barredDays working days lost to events the time bar shut out
     * @param barred the barred events, one by one
     */
    public record Position(
            LocalDate baseCompletion,
            LocalDate extendedCompletion,
            int awardedDays,
            int barredDays,
            List<Barred> barred) {

        public Position {
            barred = List.copyOf(barred);
        }

        /** Calendar days late at a date, or nought while inside the extended completion date. */
        public int daysLate(LocalDate asAt) {
            int late = SiteDates.diffDays(extendedCompletion, asAt);
            return Math.max(late, 0);
        }

        public boolean isLate(LocalDate asAt) {
            return daysLate(asAt) > 0;
        }

        /** {@code completion 2025-09-26 moved to 2025-10-08, 8 days awarded, 5 days barred}. */
        public String describe() {
            String moved = extendedCompletion.equals(baseCompletion)
                    ? "not moved"
                    : "moved to " + SiteDates.format(extendedCompletion);
            return "completion " + SiteDates.format(baseCompletion) + " " + moved + ", " + awardedDays
                    + " days awarded, " + barredDays + " days barred";
        }
    }

    /** Terms with no cap and the standard notice period. */
    public static CompletionTerms build(LocalDate baseCompletion, Money damagesPerDay) {
        return build(baseCompletion, damagesPerDay, null, null);
    }

    /**
     * Build the terms, refusing negative damages and a negative notice period.
     *
     * @param damagesCap the cap, or null for none
     * @param noticeDays the notice period, or null for the standard one
     */
    public static CompletionTerms build(
            LocalDate baseCompletion, Money damagesPerDay, Percent damagesCap, Integer noticeDays) {
        if (damagesPerDay.minor() < 0) {
            throw new BadInputException("liquidated damages cannot be negative");
        }
        int notice = noticeDays == null ? DelayEvent.DEFAULT_NOTICE_DAYS : noticeDays;
        if (notice < 0) {
            throw new BadInputException("a notice period is a whole number of days", Details.of("noticeDays", notice));
        }
        return new CompletionTerms(baseCompletion, damagesPerDay, Optional.ofNullable(damagesCap), notice);
    }

    /**
     * Where completion stands at a date.
     *
     * <p>The barred days are reported alongside the award rather than dropped, because the
     * difference between the two is the whole of what a contractor loses to poor paperwork, and
     * nobody can act on it if the report only shows the number that survived.
     */
    public Position position(List<DelayEvent> events, SiteCalendar calendar, LocalDate asAt) {
        int awarded = 0;
        int barredDays = 0;
        List<Barred> barred = new ArrayList<>();
        for (DelayEvent event : DelayEvent.eventsTo(events, asAt)) {
            Optional<BarReason> reason = event.barReason(noticeDays);
            if (reason.isEmpty()) {
                awarded += event.assessedDays(calendar, noticeDays);
                continue;
            }
            int days = event.awardedDays().orElse(0);
            int lost = days > 0 ? days : event.awardedDays().orElseGet(() -> event.workingDaysLost(calendar));
            barredDays += lost;
            barred.add(new Barred(event, reason.get(), lost));
        }
        return new Position(
                baseCompletion, calendar.addWorkingDays(baseCompletion, awarded), awarded, barredDays, barred);
    }

    /** Damages at a date, capped where the contract caps them. */
    public Money liquidatedDamages(Position position, Money contractSum, LocalDate asAt) {
        int late = position.daysLate(asAt);
        if (late == 0) {
            return Money.zero(damagesPerDay.currency());
        }
        Money raw = damagesPerDay.scale(late);
        return damagesCap.map(cap -> Money.min(raw, cap.of(contractSum))).orElse(raw);
    }

    /** Whether the damages at a date have run into the cap. */
    public boolean isDamagesCapped(Position position, Money contractSum, LocalDate asAt) {
        if (damagesCap.isEmpty()) {
            return false;
        }
        Money raw = damagesPerDay.scale(position.daysLate(asAt));
        return raw.minor() > damagesCap.get().of(contractSum).minor();
    }
}
