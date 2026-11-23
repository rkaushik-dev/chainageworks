package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;

/**
 * An advance payment for mobilisation, and the deductions that pay it back.
 *
 * <p>The employer pays the advance before much is built and takes it back out of the interim
 * certificates once the works are properly under way: nothing is deducted until the gross
 * valuation passes a share of the contract sum, and after that a fixed share of every pound
 * certified goes back until the whole advance has been repaid. Worked cumulatively, the deduction to
 * date is that share of the gross above the threshold, never more than the advance itself.
 *
 * @param amount the advance paid
 * @param paidOn when it was paid, which is when it first appears on a certificate
 * @param repaymentFrom the share of the contract sum the gross has to pass before repayment starts
 * @param repaymentRate the share of the gross above that threshold deducted towards repayment
 */
public record AdvancePayment(Money amount, LocalDate paidOn, Percent repaymentFrom, Percent repaymentRate) {

    /** Repayment starts once a tenth of the contract sum has been valued. */
    public static final Percent DEFAULT_REPAYMENT_FROM = Percent.of(10);

    /** A quarter of each certificate goes towards repaying the advance. */
    public static final Percent DEFAULT_REPAYMENT_RATE = Percent.of(25);

    /** An advance repaid on the usual terms. */
    public static AdvancePayment of(Money amount, LocalDate paidOn) {
        return build(amount, paidOn, null, null);
    }

    /**
     * Build an advance, refusing an advance of nothing, a threshold outside 0 to 100 percent and a
     * repayment rate that would never repay anything.
     *
     * @param repaymentFrom the threshold, or null for {@link #DEFAULT_REPAYMENT_FROM}
     * @param repaymentRate the rate, or null for {@link #DEFAULT_REPAYMENT_RATE}
     */
    public static AdvancePayment build(Money amount, LocalDate paidOn, Percent repaymentFrom, Percent repaymentRate) {
        if (amount.minor() <= 0) {
            throw new BadInputException(
                    "an advance payment has to be more than nothing", Details.of("amount", amount.format()));
        }
        Percent from = repaymentFrom == null ? DEFAULT_REPAYMENT_FROM : repaymentFrom;
        Percent rate = repaymentRate == null ? DEFAULT_REPAYMENT_RATE : repaymentRate;
        if (from.basisPoints() < 0 || from.compareTo(Percent.FULL) > 0) {
            throw new BadInputException(
                    "repayment of an advance starts between 0 and 100 percent of the contract sum",
                    Details.of("repaymentFrom", from.format()));
        }
        if (rate.basisPoints() <= 0 || rate.compareTo(Percent.FULL) > 0) {
            throw new BadInputException(
                    "an advance is repaid at more than nothing and at most the whole certificate",
                    Details.of("repaymentRate", rate.format()));
        }
        return new AdvancePayment(amount, paidOn, from, rate);
    }

    /** The advance standing on the certificate at a cut-off: all of it once paid, nothing before. */
    public Money paidAt(LocalDate asAt) {
        return SiteDates.compare(paidOn, asAt) <= 0 ? amount : Money.zero(amount.currency());
    }

    /**
     * What has been deducted towards repaying the advance by a cut-off.
     *
     * @param gross the gross valuation to date
     * @param contractSum the contract sum the threshold is set against
     */
    public Money repaidAt(Money gross, Money contractSum, LocalDate asAt) {
        Money zero = Money.zero(amount.currency());
        if (paidAt(asAt).isZero()) {
            return zero;
        }
        Money above = gross.minus(repaymentFrom.of(contractSum));
        if (above.minor() <= 0) {
            return zero;
        }
        return Money.min(repaymentRate.of(above), amount);
    }

    /** True once the deductions have taken the whole advance back. */
    public boolean isRepaidAt(Money gross, Money contractSum, LocalDate asAt) {
        return repaidAt(gross, contractSum, asAt).equals(amount);
    }
}
