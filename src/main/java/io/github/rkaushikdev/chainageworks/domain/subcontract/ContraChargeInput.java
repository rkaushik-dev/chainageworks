package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.time.LocalDate;

/**
 * What a contra charge is built from. A {@code null} reference takes the id, a {@code null} reason
 * means other, a {@code null} notice date means unnotified and a {@code null} dispute flag means
 * undisputed.
 *
 * @param id the charge's key
 * @param subcontractId the order it is raised against
 * @param reference the contra charge reference
 * @param reason what it is for
 * @param description what happened
 * @param amount what it is for
 * @param incurredOn the day the cost was incurred
 * @param notifiedOn the day the subcontractor was told
 * @param disputed whether the subcontractor is arguing about it
 */
public record ContraChargeInput(
        String id,
        String subcontractId,
        String reference,
        ContraReason reason,
        String description,
        Money amount,
        LocalDate incurredOn,
        LocalDate notifiedOn,
        Boolean disputed) {

    /** An undisputed, unnotified charge for something other. */
    public static ContraChargeInput of(
            String id, String subcontractId, String description, Money amount, LocalDate incurredOn) {
        return new ContraChargeInput(id, subcontractId, null, null, description, amount, incurredOn, null, null);
    }

    /** The input a charge was built from, to change and build again. */
    public static ContraChargeInput from(ContraCharge charge) {
        return new ContraChargeInput(
                charge.id(),
                charge.subcontractId(),
                charge.reference(),
                charge.reason(),
                charge.description(),
                charge.amount(),
                charge.incurredOn(),
                charge.notifiedOn().orElse(null),
                charge.disputed());
    }

    public ContraChargeInput withId(String other) {
        return new ContraChargeInput(
                other, subcontractId, reference, reason, description, amount, incurredOn, notifiedOn, disputed);
    }

    public ContraChargeInput withReference(String other) {
        return new ContraChargeInput(
                id, subcontractId, other, reason, description, amount, incurredOn, notifiedOn, disputed);
    }

    public ContraChargeInput withReason(ContraReason other) {
        return new ContraChargeInput(
                id, subcontractId, reference, other, description, amount, incurredOn, notifiedOn, disputed);
    }

    public ContraChargeInput withNotifiedOn(LocalDate other) {
        return new ContraChargeInput(
                id, subcontractId, reference, reason, description, amount, incurredOn, other, disputed);
    }

    public ContraChargeInput withDisputed(boolean other) {
        return new ContraChargeInput(
                id, subcontractId, reference, reason, description, amount, incurredOn, notifiedOn, other);
    }
}
