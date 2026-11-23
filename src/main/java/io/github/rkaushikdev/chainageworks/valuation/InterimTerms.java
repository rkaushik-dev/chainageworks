package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.util.Optional;

/**
 * The terms the contract sets on issuing interim certificates, apart from retention.
 *
 * <p>The minimum amount is the one that surprises people. The engineer is not bound to certify a
 * month that moves the account by less than it, so a quiet month is held over and paid with the
 * next one rather than costing everybody a certificate for a few hundred pounds.
 *
 * @param minimumCertificate the smallest amount a certificate is issued for, empty for no minimum
 * @param advance an advance payment the certificates repay, empty when none was made
 */
public record InterimTerms(Optional<Money> minimumCertificate, Optional<AdvancePayment> advance) {

    /** No minimum and no advance: every month is certified, however little it moves. */
    public static final InterimTerms NONE = new InterimTerms(Optional.empty(), Optional.empty());

    public InterimTerms {
        minimumCertificate = minimumCertificate == null ? Optional.empty() : minimumCertificate;
        advance = advance == null ? Optional.empty() : advance;
    }

    /**
     * Terms with a minimum certificate.
     *
     * @throws BadInputException for a minimum below nothing
     */
    public static InterimTerms withMinimum(Money minimum) {
        return NONE.andMinimum(minimum);
    }

    /**
     * These terms with a minimum certificate.
     *
     * @throws BadInputException for a minimum below nothing
     */
    public InterimTerms andMinimum(Money minimum) {
        if (minimum.isNegative()) {
            throw new BadInputException(
                    "the minimum amount of a certificate cannot be negative", Details.of("minimum", minimum.format()));
        }
        return new InterimTerms(Optional.of(minimum), advance);
    }

    /** These terms with an advance payment, or with none when {@code other} is null. */
    public InterimTerms withAdvance(AdvancePayment other) {
        return new InterimTerms(minimumCertificate, Optional.ofNullable(other));
    }

    /**
     * Whether a certificate that would pay {@code due} is held over to the next month.
     *
     * <p>Only a positive amount below the minimum is held. A month that values less than the last
     * one is still certified, because holding a negative over would leave the contractor holding
     * money the account says it has been overpaid.
     */
    public boolean holdsOver(Money due) {
        return minimumCertificate
                .map(minimum -> due.minor() > 0 && due.minor() < minimum.minor())
                .orElse(false);
    }
}
