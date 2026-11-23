package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.List;

/**
 * What the fluctuation formula adds to, or takes off, a valuation.
 *
 * @param asAt the date it was worked out for
 * @param base the valuation it applies to
 * @param adjustable the part of the base the formula is allowed to move
 * @param factor the factor that was applied
 * @param amount what the formula adds, negative when the indices have fallen
 * @param provisional true while any figure behind it is still provisional
 */
public record PriceAdjustment(
        LocalDate asAt, Money base, Money adjustable, AdjustmentFactor factor, Money amount, boolean provisional) {

    /** An adjustment of nothing, for a contract with no fluctuation clause. */
    public static PriceAdjustment none(Money base, LocalDate asAt) {
        return new PriceAdjustment(
                asAt,
                base,
                Money.zero(base.currency()),
                new AdjustmentFactor(asAt, asAt, List.of(), 0, false),
                Money.zero(base.currency()),
                false);
    }

    /** {@code factor 7.500 percent on 90000 adjustable, firm}. */
    public String describe() {
        String state = provisional ? "provisional" : "firm";
        return "factor " + Numbers.toFixed(factor.factor() * 100, 3) + " percent on "
                + Numbers.format((double) adjustable.minor() / 100) + " adjustable, " + state;
    }
}
