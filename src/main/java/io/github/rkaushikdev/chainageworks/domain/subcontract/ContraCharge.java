package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Collation;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A charge the main contractor sets against a subcontractor's payment.
 *
 * @param id the charge's key
 * @param subcontractId the order it is raised against
 * @param reference the contra charge reference
 * @param reason what it is for
 * @param description what happened
 * @param amount what it is for
 * @param incurredOn the day the cost was incurred
 * @param notifiedOn when the subcontractor was told; absent means nobody has told them yet
 * @param disputed whether the subcontractor is arguing about it
 */
public record ContraCharge(
        String id,
        String subcontractId,
        String reference,
        ContraReason reason,
        String description,
        Money amount,
        LocalDate incurredOn,
        Optional<LocalDate> notifiedOn,
        boolean disputed) {

    /** Every reason, in the order the register lists them. */
    public static final List<ContraReason> REASONS = List.of(ContraReason.values());

    public ContraCharge {
        notifiedOn = notifiedOn == null ? Optional.empty() : notifiedOn;
    }

    /**
     * The charges standing against one order at a date.
     *
     * @param subcontractId the order
     * @param asAt the date
     * @param raised everything raised against the order by the date, disputed or not
     * @param deductible what may actually come off the next payment
     * @param disputed deductible, but the subcontractor is arguing about it
     * @param barred charges that lost their right to be set off
     * @param charges the charges, earliest first
     */
    public record Position(
            String subcontractId,
            LocalDate asAt,
            Money raised,
            Money deductible,
            Money disputed,
            Money barred,
            List<ContraCharge> charges) {

        public Position {
            charges = List.copyOf(charges);
        }

        /**
         * Take the deductible charges off a payment, never below nothing.
         *
         * <p>A set off larger than the payment does not become a debt the subcontractor owes on
         * this certificate. It waits for the next one, which is what the contract says and also the
         * only way the arithmetic stays honest when the account is finally settled.
         */
        public Money applyTo(Money payable) {
            Money after = payable.minus(deductible);
            return after.minor() < 0 ? Money.zero(payable.currency()) : after;
        }

        /** What could not be taken this time and rolls into the next payment. */
        public Money carriedForward(Money payable) {
            Money after = payable.minus(deductible);
            return after.minor() < 0 ? after.negate() : Money.zero(payable.currency());
        }

        /** {@code SC-EARTH at 2025-03-25, 4 charges, 6000 deductible}. */
        public String describe() {
            return subcontractId + " at " + SiteDates.format(asAt) + ", " + charges.size() + " charges, "
                    + Numbers.format((double) deductible.minor() / 100) + " deductible";
        }
    }

    /** Build a charge, refusing a negative amount, a blank description and notice before the cost. */
    public static ContraCharge build(ContraChargeInput input) {
        if (input.amount().minor() < 0) {
            throw new BadInputException("a contra charge cannot be negative", Details.of("charge", input.id()));
        }
        if (Text.trim(input.description()).isEmpty()) {
            throw new BadInputException("a contra charge has to say what it is for", Details.of("charge", input.id()));
        }
        Optional<LocalDate> notifiedOn = Optional.ofNullable(input.notifiedOn());
        if (notifiedOn.isPresent() && notifiedOn.get().isBefore(input.incurredOn())) {
            throw new BadInputException(
                    "a charge cannot be notified before it was incurred",
                    Details.of(
                            "charge", input.id(),
                            "incurredOn", SiteDates.format(input.incurredOn()),
                            "notifiedOn", SiteDates.format(notifiedOn.get())));
        }
        return new ContraCharge(
                input.id(),
                input.subcontractId(),
                input.reference() == null ? input.id() : input.reference(),
                input.reason() == null ? ContraReason.OTHER : input.reason(),
                Text.trim(input.description()),
                input.amount(),
                input.incurredOn(),
                notifiedOn,
                input.disputed() != null && input.disputed());
    }

    /**
     * Whether the charge may be set against a payment at a date.
     *
     * <p>A charge the order already covers stands on the day it is incurred. Anything else has to
     * have been notified, and notified inside the window, because a charge produced for the first
     * time on a final account is the one thing every subcontract dispute has in common.
     */
    public boolean isDeductibleAt(LocalDate asAt, int notifyDays) {
        if (incurredOn.isAfter(asAt)) {
            return false;
        }
        if (reason.agreedInOrder()) {
            return true;
        }
        if (notifiedOn.isEmpty() || notifiedOn.get().isAfter(asAt)) {
            return false;
        }
        return !notifiedOn.get().isAfter(incurredOn.plusDays(notifyDays));
    }

    /** Charges that lost their right to be set off because nobody served notice in time. */
    public static List<ContraCharge> barred(List<ContraCharge> charges, LocalDate asAt, int notifyDays) {
        return charges.stream()
                .filter(charge -> !charge.incurredOn().isAfter(asAt)
                        && !charge.reason().agreedInOrder()
                        && !charge.isDeductibleAt(asAt, notifyDays)
                        && charge.incurredOn().plusDays(notifyDays).isBefore(asAt))
                .toList();
    }

    /** The charges standing against one order at a date. */
    public static Position position(
            List<ContraCharge> charges, String subcontractId, LocalDate asAt, int notifyDays, Currency currency) {
        List<ContraCharge> mine = new ArrayList<>(charges.stream()
                .filter(c -> c.subcontractId().equals(subcontractId)
                        && !c.incurredOn().isAfter(asAt))
                .toList());
        mine.sort((a, b) -> {
            int byDate = a.incurredOn().compareTo(b.incurredOn());
            return byDate != 0 ? byDate : Collation.compare(a.id(), b.id());
        });
        List<ContraCharge> deductible =
                mine.stream().filter(c -> c.isDeductibleAt(asAt, notifyDays)).toList();
        return new Position(
                subcontractId,
                asAt,
                Money.sum(mine.stream().map(ContraCharge::amount).toList(), currency),
                Money.sum(deductible.stream().map(ContraCharge::amount).toList(), currency),
                Money.sum(
                        deductible.stream()
                                .filter(ContraCharge::disputed)
                                .map(ContraCharge::amount)
                                .toList(),
                        currency),
                Money.sum(
                        barred(mine, asAt, notifyDays).stream()
                                .map(ContraCharge::amount)
                                .toList(),
                        currency),
                mine);
    }
}
