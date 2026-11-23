package io.github.rkaushikdev.chainageworks.domain.risk;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One risk on the early warning register: what it is, who carries it, what it would cost and how
 * likely it is.
 *
 * @param id the warning's key
 * @param reference the register reference
 * @param description what the risk is
 * @param owner who carries it if it happens
 * @param state where it stands
 * @param extent where on the alignment, when it is a place rather than a whole job risk
 * @param raisedOn the day it was raised
 * @param meetingOn the meeting the contract requires; absent means nobody has held one
 * @param closedOn the day it was closed
 * @param costIfItHappens what it costs if it happens, before any weighting
 * @param likelihood how likely it is
 * @param delayDays working days it would add to the programme
 * @param quotation what the compensation event was quoted at once the risk happened, which
 *     replaces the estimate made when it was only a risk
 */
public record EarlyWarning(
        String id,
        String reference,
        String description,
        RiskOwner owner,
        RiskState state,
        Optional<ChainageRange> extent,
        LocalDate raisedOn,
        Optional<LocalDate> meetingOn,
        Optional<LocalDate> closedOn,
        Money costIfItHappens,
        Percent likelihood,
        int delayDays,
        Optional<Money> quotation) {

    /** Days from raising a warning to the meeting the contract wants. */
    public static final int DEFAULT_MEETING_DAYS = 14;

    public EarlyWarning {
        extent = extent == null ? Optional.empty() : extent;
        meetingOn = meetingOn == null ? Optional.empty() : meetingOn;
        closedOn = closedOn == null ? Optional.empty() : closedOn;
        quotation = quotation == null ? Optional.empty() : quotation;
    }

    /**
     * Where the register stands at a date.
     *
     * @param asAt the date
     * @param open the open warnings, dearest expected cost first
     * @param expected everything open, weighted by likelihood
     * @param worstCase everything open at full value, which is the worst case
     * @param carried the part of the expected cost the contractor carries
     * @param overdueMeetings open warnings whose meeting was not held in time
     * @param delayDays working days of delay in the open risks, if every one happened
     */
    public record Position(
            LocalDate asAt,
            List<EarlyWarning> open,
            Money expected,
            Money worstCase,
            Money carried,
            List<EarlyWarning> overdueMeetings,
            int delayDays) {

        public Position {
            open = List.copyOf(open);
            overdueMeetings = List.copyOf(overdueMeetings);
        }

        /** Risks nobody has agreed an owner for, which is the argument waiting to start. */
        public List<EarlyWarning> unowned() {
            return open.stream().filter(w -> w.owner() == RiskOwner.UNRESOLVED).toList();
        }
    }

    /**
     * Build a warning, refusing one that says nothing, a negative cost, a likelihood outside 0 to
     * 100, a negative delay and a close before it was raised.
     */
    public static EarlyWarning build(EarlyWarningInput input) {
        if (Text.trim(input.description()).isEmpty()) {
            throw new BadInputException(
                    "an early warning has to say what the risk is", Details.of("warning", input.id()));
        }
        if (input.costIfItHappens().minor() < 0) {
            throw new BadInputException("a risk cannot cost less than nothing", Details.of("warning", input.id()));
        }
        Percent likelihood = input.likelihood() == null ? Percent.of(50) : input.likelihood();
        if (likelihood.basisPoints() < 0 || likelihood.basisPoints() > Percent.BASIS_POINTS_WHOLE) {
            throw new BadInputException("a likelihood runs from 0 to 100", Details.of("warning", input.id()));
        }
        int delayDays = input.delayDays() == null ? 0 : input.delayDays();
        if (delayDays < 0) {
            throw new BadInputException(
                    "a delay is a whole number of working days",
                    Details.of("warning", input.id(), "delayDays", delayDays));
        }
        RiskState state = input.state() == null ? RiskState.OPEN : input.state();
        Optional<Money> quotation = Optional.ofNullable(input.quotation());
        if (quotation.isPresent() && state != RiskState.HAPPENED) {
            throw new BadInputException(
                    "only a risk that happened can be quoted as a compensation event",
                    Details.of("warning", input.id(), "state", state.wire()));
        }
        if (quotation.isPresent() && quotation.get().minor() < 0) {
            throw new BadInputException("a quotation cannot be less than nothing", Details.of("warning", input.id()));
        }
        if (quotation.isPresent()
                && quotation.get().currency() != input.costIfItHappens().currency()) {
            throw new BadInputException(
                    "a quotation is in another currency from the risk", Details.of("warning", input.id()));
        }
        Optional<LocalDate> closedOn = Optional.ofNullable(input.closedOn());
        if (closedOn.isPresent() && closedOn.get().isBefore(input.raisedOn())) {
            throw new BadInputException(
                    "a warning cannot close before it was raised", Details.of("warning", input.id()));
        }
        return new EarlyWarning(
                input.id(),
                input.reference() == null ? input.id() : input.reference(),
                Text.trim(input.description()),
                input.owner(),
                state,
                Optional.ofNullable(input.extent()),
                input.raisedOn(),
                Optional.ofNullable(input.meetingOn()),
                closedOn,
                input.costIfItHappens(),
                likelihood,
                delayDays,
                quotation);
    }

    /** Raised by {@code asAt} and not closed on that day. */
    public boolean isOpenAt(LocalDate asAt) {
        if (raisedOn.isAfter(asAt)) {
            return false;
        }
        return closedOn.map(on -> on.isAfter(asAt)).orElse(true);
    }

    /**
     * A warning whose meeting was never held, or was held late.
     *
     * <p>Held separately from the risk itself because it is a different failure. The risk may still
     * be small; the process failure is what the other side quotes back when the claim arrives.
     */
    public boolean meetingOverdue(LocalDate asAt, int meetingDays) {
        LocalDate due = raisedOn.plusDays(meetingDays);
        return meetingOn.map(on -> on.isAfter(due)).orElseGet(() -> due.isBefore(asAt));
    }

    public boolean meetingOverdue(LocalDate asAt) {
        return meetingOverdue(asAt, DEFAULT_MEETING_DAYS);
    }

    /**
     * The cost weighted by how likely it is, which is what a forecast carries. A risk that happened
     * is carried in full, at its quotation once it has one.
     */
    public Money expectedCost() {
        return switch (state) {
            case AVOIDED -> Money.zero(costIfItHappens.currency());
            case HAPPENED -> fullCost();
            case OPEN, MITIGATED -> costIfItHappens.scale(likelihood.fraction());
        };
    }

    /** What the risk costs at full value: the quotation once it has one, the estimate until then. */
    public Money fullCost() {
        return quotation.orElse(costIfItHappens);
    }

    /** True for a risk that happened and has not been quoted yet, which is the next thing to chase. */
    public boolean awaitsQuotation() {
        return state == RiskState.HAPPENED && quotation.isEmpty();
    }

    private double contractorShare() {
        return switch (owner) {
            case CONTRACTOR -> 1;
            case SHARED -> 0.5;
            case EMPLOYER, UNRESOLVED -> 0;
        };
    }

    /** {@link #position(List, LocalDate, Currency, int)} with the standard meeting window. */
    public static Position position(List<EarlyWarning> warnings, LocalDate asAt, Currency currency) {
        return position(warnings, asAt, currency, DEFAULT_MEETING_DAYS);
    }

    /**
     * Where the register stands at a date.
     *
     * <p>A shared risk is counted half to the contractor, which is a convention rather than a rule,
     * and it is stated here rather than buried in a report so the two sides can argue about the
     * convention instead of about the number.
     */
    public static Position position(List<EarlyWarning> warnings, LocalDate asAt, Currency currency, int meetingDays) {
        List<EarlyWarning> open =
                new ArrayList<>(warnings.stream().filter(w -> w.isOpenAt(asAt)).toList());
        open.sort((a, b) ->
                Long.compare(b.expectedCost().minor(), a.expectedCost().minor()));
        int delayDays = 0;
        for (EarlyWarning warning : open) {
            delayDays += warning.delayDays();
        }
        return new Position(
                asAt,
                open,
                Money.sum(open.stream().map(EarlyWarning::expectedCost).toList(), currency),
                Money.sum(open.stream().map(EarlyWarning::fullCost).toList(), currency),
                Money.sum(
                        open.stream()
                                .map(w -> w.expectedCost().scale(w.contractorShare()))
                                .toList(),
                        currency),
                open.stream().filter(w -> w.meetingOverdue(asAt, meetingDays)).toList(),
                delayDays);
    }

    /** {@code EW/2025/001 employer open, met 2025-02-18}. */
    public String describe() {
        String meeting = meetingOn.map(on -> "met " + SiteDates.format(on)).orElse("no meeting held");
        return reference + " " + owner.wire() + " " + state.wire() + ", " + meeting;
    }
}
