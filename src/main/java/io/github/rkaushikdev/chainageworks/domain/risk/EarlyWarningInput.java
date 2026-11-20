package io.github.rkaushikdev.chainageworks.domain.risk;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.time.LocalDate;

/**
 * What an early warning is built from. A {@code null} reference takes the id, a {@code null} state
 * means open, a {@code null} likelihood means even odds, and a {@code null} delay means none.
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
        Integer delayDays) {

    /** An open warning with even odds, no delay, no meeting and no extent. */
    public static EarlyWarningInput of(
            String id, String description, RiskOwner owner, LocalDate raisedOn, Money costIfItHappens) {
        return new EarlyWarningInput(
                id, null, description, owner, null, null, raisedOn, null, null, costIfItHappens, null, null);
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
                warning.delayDays());
    }

    public EarlyWarningInput withId(String other) {
        return new EarlyWarningInput(
                other,
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
                delayDays);
    }

    public EarlyWarningInput withReference(String other) {
        return new EarlyWarningInput(
                id,
                other,
                description,
                owner,
                state,
                extent,
                raisedOn,
                meetingOn,
                closedOn,
                costIfItHappens,
                likelihood,
                delayDays);
    }

    public EarlyWarningInput withState(RiskState other) {
        return new EarlyWarningInput(
                id,
                reference,
                description,
                owner,
                other,
                extent,
                raisedOn,
                meetingOn,
                closedOn,
                costIfItHappens,
                likelihood,
                delayDays);
    }

    public EarlyWarningInput withExtent(ChainageRange other) {
        return new EarlyWarningInput(
                id,
                reference,
                description,
                owner,
                state,
                other,
                raisedOn,
                meetingOn,
                closedOn,
                costIfItHappens,
                likelihood,
                delayDays);
    }

    public EarlyWarningInput withMeetingOn(LocalDate other) {
        return new EarlyWarningInput(
                id,
                reference,
                description,
                owner,
                state,
                extent,
                raisedOn,
                other,
                closedOn,
                costIfItHappens,
                likelihood,
                delayDays);
    }

    public EarlyWarningInput withClosedOn(LocalDate other) {
        return new EarlyWarningInput(
                id,
                reference,
                description,
                owner,
                state,
                extent,
                raisedOn,
                meetingOn,
                other,
                costIfItHappens,
                likelihood,
                delayDays);
    }

    public EarlyWarningInput withLikelihood(Percent other) {
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
                other,
                delayDays);
    }

    public EarlyWarningInput withDelayDays(Integer other) {
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
                other);
    }
}
