package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.time.LocalDate;

/**
 * A subcontractor's application for payment.
 *
 * @param id the application's key
 * @param subcontractId the subcontract
 * @param periodNumber which period it is for
 * @param appliedOn the day it was made
 * @param gross the cumulative gross the subcontractor says it has earned
 * @param previouslyPaid what has been paid before this application
 */
public record PaymentApplication(
        String id, String subcontractId, int periodNumber, LocalDate appliedOn, Money gross, Money previouslyPaid) {

    public PaymentApplication withId(String other) {
        return new PaymentApplication(other, subcontractId, periodNumber, appliedOn, gross, previouslyPaid);
    }

    public PaymentApplication withPeriod(int number, LocalDate on) {
        return new PaymentApplication(id, subcontractId, number, on, gross, previouslyPaid);
    }

    public PaymentApplication withPreviouslyPaid(Money other) {
        return new PaymentApplication(id, subcontractId, periodNumber, appliedOn, gross, other);
    }
}
