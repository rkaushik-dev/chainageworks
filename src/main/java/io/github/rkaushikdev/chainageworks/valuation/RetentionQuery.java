package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.time.LocalDate;

/**
 * What a retention figure is worked out from.
 *
 * @param grossValuation the gross valuation to date
 * @param contractSum the contract sum the limit is set against
 * @param terms the retention terms
 * @param substantialCompletionOn the date the works were taken over; null when they have not been
 * @param defectsCertificateOn the date of the defects certificate; null when there is none
 * @param asAt the cut-off
 */
public record RetentionQuery(
        Money grossValuation,
        Money contractSum,
        RetentionTerms terms,
        LocalDate substantialCompletionOn,
        LocalDate defectsCertificateOn,
        LocalDate asAt) {

    public static RetentionQuery of(Money grossValuation, Money contractSum, RetentionTerms terms, LocalDate asAt) {
        return new RetentionQuery(grossValuation, contractSum, terms, null, null, asAt);
    }

    public RetentionQuery withSubstantialCompletionOn(LocalDate other) {
        return new RetentionQuery(grossValuation, contractSum, terms, other, defectsCertificateOn, asAt);
    }

    public RetentionQuery withDefectsCertificateOn(LocalDate other) {
        return new RetentionQuery(grossValuation, contractSum, terms, substantialCompletionOn, other, asAt);
    }

    public RetentionQuery withAsAt(LocalDate other) {
        return new RetentionQuery(
                grossValuation, contractSum, terms, substantialCompletionOn, defectsCertificateOn, other);
    }
}
