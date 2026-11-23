package io.github.rkaushikdev.chainageworks.domain.risk;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.time.LocalDate;

/**
 * What an early warning is built from. A {@code null} reference takes the id, a {@code null} state
 * means open, a {@code null} likelihood means even odds, a {@code null} delay means none, and a
 * {@code null} quotation means none has been given.
 *
 * @param id the warning's key
 * @param reference the register reference
 * @param description what the risk is
 * @param owner who carries it if it happens
 * @param state where it stands
 * @param extent where on the alignment
 * @param raisedOn the day it was raised
 * @param meetingOn the day the risk reduction meeting was held
 * @param closedOn the day it was closed
 * @param costIfItHappens what it costs if it happens, before any weighting
 * @param likelihood how likely it is
 * @param delayDays working days it would add to the programme
 * @param quotation the quotation for the compensation event once the risk has happened
 */
public record EarlyWarningInput(
        String id,
        String reference,
        String description,
        RiskOwner owner,
        RiskState state,
        ChainageRange extent,
        LocalDate raisedOn,
        LocalDate meetingOn,
        LocalDate closedOn,
        Money costIfItHappens,
        Percent likelihood,
        Integer delayDays,
        Money quotation) {

    /** An open warning with even odds, no delay, no meeting and no extent. */
    public static EarlyWarningInput of(
            String id, String description, RiskOwner owner, LocalDate raisedOn, Money costIfItHappens) {
        return new EarlyWarningInput(
                id, null, description, owner, null, null, raisedOn, null, null, costIfItHappens, null, null, null);
    }

    /** The input a warning was built from, to change and build again. */
    public static EarlyWarningInput from(EarlyWarning warning) {
        return new EarlyWarningInput(
                warning.id(),
                warning.reference(),
                warning.description(),
                warning.owner(),
                warning.state(),
                warning.extent().orElse(null),
                warning.raisedOn(),
                warning.meetingOn().orElse(null),
                warning.closedOn().orElse(null),
                warning.costIfItHappens(),
                warning.likelihood(),
                warning.delayDays(),
                warning.quotation().orElse(null));
    }

    public EarlyWarningInput withId(String other) {
        Draft draft = new Draft(this);
        draft.id = other;
        return draft.input();
    }

    public EarlyWarningInput withReference(String other) {
        Draft draft = new Draft(this);
        draft.reference = other;
        return draft.input();
    }

    public EarlyWarningInput withState(RiskState other) {
        Draft draft = new Draft(this);
        draft.state = other;
        return draft.input();
    }

    public EarlyWarningInput withExtent(ChainageRange other) {
        Draft draft = new Draft(this);
        draft.extent = other;
        return draft.input();
    }

    public EarlyWarningInput withMeetingOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.meetingOn = other;
        return draft.input();
    }

    public EarlyWarningInput withClosedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.closedOn = other;
        return draft.input();
    }

    public EarlyWarningInput withLikelihood(Percent other) {
        Draft draft = new Draft(this);
        draft.likelihood = other;
        return draft.input();
    }

    public EarlyWarningInput withDelayDays(Integer other) {
        Draft draft = new Draft(this);
        draft.delayDays = other;
        return draft.input();
    }

    /** The quotation given for the compensation event the risk became. */
    public EarlyWarningInput withQuotation(Money other) {
        Draft draft = new Draft(this);
        draft.quotation = other;
        return draft.input();
    }

    /** A copy of the input with one field changed, so each wither names only its own field. */
    private static final class Draft {
        private String id;
        private String reference;
        private String description;
        private RiskOwner owner;
        private RiskState state;
        private ChainageRange extent;
        private LocalDate raisedOn;
        private LocalDate meetingOn;
        private LocalDate closedOn;
        private Money costIfItHappens;
        private Percent likelihood;
        private Integer delayDays;
        private Money quotation;

        private Draft(EarlyWarningInput input) {
            id = input.id;
            reference = input.reference;
            description = input.description;
            owner = input.owner;
            state = input.state;
            extent = input.extent;
            raisedOn = input.raisedOn;
            meetingOn = input.meetingOn;
            closedOn = input.closedOn;
            costIfItHappens = input.costIfItHappens;
            likelihood = input.likelihood;
            delayDays = input.delayDays;
            quotation = input.quotation;
        }

        private EarlyWarningInput input() {
            return new EarlyWarningInput(
                    id,
                    reference,
                    description,
                    owner,
                    state,
                    extent,
                    raisedOn,
                    meetingOn,
                    closedOn,
                    costIfItHappens,
                    likelihood,
                    delayDays,
                    quotation);
        }
    }
}
