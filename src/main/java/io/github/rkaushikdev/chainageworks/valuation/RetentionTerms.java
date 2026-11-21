package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.util.Map;

/**
 * The retention terms of the contract.
 *
 * <p>Two percentages that are easy to run together and mean different things. The rate is what is
 * held off each valuation. The limit is a cap on the running total, expressed against the contract
 * sum, and once it bites the rate stops mattering entirely.
 *
 * @param rate what is held off each valuation
 * @param limit cap on the total held, as a share of the contract sum
 * @param releaseOnCompletion share of the held total released at substantial completion
 */
public record RetentionTerms(Percent rate, Percent limit, Percent releaseOnCompletion) {

    public static final RetentionTerms DEFAULT = new RetentionTerms(Percent.of(5), Percent.of(3), Percent.of(50));

    /** Check a set of terms, handing the same terms back. */
    public static RetentionTerms build(RetentionTerms input) {
        for (Map.Entry<String, Percent> entry : Map.of(
                        "rate", input.rate(),
                        "limit", input.limit(),
                        "releaseOnCompletion", input.releaseOnCompletion())
                .entrySet()) {
            long points = entry.getValue().basisPoints();
            if (points < 0 || points > Percent.FULL.basisPoints()) {
                throw new BadInputException(
                        "a retention percentage runs from 0 to 100", Details.of("field", entry.getKey()));
            }
        }
        return input;
    }

    public RetentionTerms withRate(Percent other) {
        return new RetentionTerms(other, limit, releaseOnCompletion);
    }

    public RetentionTerms withLimit(Percent other) {
        return new RetentionTerms(rate, other, releaseOnCompletion);
    }

    public RetentionTerms withReleaseOnCompletion(Percent other) {
        return new RetentionTerms(rate, limit, other);
    }

    /** The proportion of a valuation that is paid across after retention. */
    public Percent payableShare() {
        return rate.complement();
    }
}
