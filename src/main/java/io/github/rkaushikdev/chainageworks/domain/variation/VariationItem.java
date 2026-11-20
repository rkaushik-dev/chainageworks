package io.github.rkaushikdev.chainageworks.domain.variation;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.util.Optional;

/**
 * One bill item a variation changes.
 *
 * @param code the bill item
 * @param quantityChange signed: an omission is negative
 * @param rate a rate of its own, or absent to use whatever the bill says
 * @param basis what the change is priced on
 */
public record VariationItem(ItemCode code, Quantity quantityChange, Optional<Rate> rate, ValuationBasis basis) {

    public VariationItem {
        rate = rate == null ? Optional.empty() : rate;
    }

    /** A change priced at the bill rate. */
    public static VariationItem atBillRates(ItemCode code, Quantity change) {
        return new VariationItem(code, change, Optional.empty(), ValuationBasis.BILL_RATES);
    }

    /** A change priced at a rate of its own. */
    public static VariationItem priced(ItemCode code, Quantity change, Rate rate, ValuationBasis basis) {
        return new VariationItem(code, change, Optional.of(rate), basis);
    }

    public VariationItem withCode(ItemCode other) {
        return new VariationItem(other, quantityChange, rate, basis);
    }

    public VariationItem withRate(Rate other) {
        return new VariationItem(code, quantityChange, Optional.ofNullable(other), basis);
    }

    public VariationItem withBasis(ValuationBasis other) {
        return new VariationItem(code, quantityChange, rate, other);
    }
}
