package io.github.rkaushikdev.chainageworks.domain.variation;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.util.Optional;

/**
 * One bill item a variation changes.
 *
 * <p>A pro rata rate is usually not typed in at all. It is a bill rate for similar work moved by a
 * factor for whatever is different, deeper, harder or smaller in quantity, and recording it that
 * way means a revision of the bill rate carries through to the variation instead of leaving a stale
 * copy of it behind.
 *
 * @param code the bill item
 * @param quantityChange signed: an omission is negative
 * @param rate a rate of its own, or absent to use whatever the bill says
 * @param basis what the change is priced on
 * @param proRata the bill rate a pro rata rate is built from, absent when the rate is typed in
 */
public record VariationItem(
        ItemCode code, Quantity quantityChange, Optional<Rate> rate, ValuationBasis basis, Optional<ProRata> proRata) {

    public VariationItem {
        rate = rate == null ? Optional.empty() : rate;
        proRata = proRata == null ? Optional.empty() : proRata;
    }

    /**
     * A pro rata rate: the rate of a bill item for similar work, moved by a factor.
     *
     * @param from the bill item whose rate it starts from
     * @param factor what that rate is multiplied by, above one for harder work and below for easier
     */
    public record ProRata(ItemCode from, double factor) {

        /** The rate this builds against {@code bill}, refusing a source item the bill does not carry. */
        public Rate rateIn(Bill bill) {
            return bill.find(from)
                    .orElseThrow(() -> new NotFoundException("bill item", from.format()))
                    .rate()
                    .scale(factor);
        }
    }

    /** A change priced in the shape the other constructors take, with no pro rata source. */
    public VariationItem(ItemCode code, Quantity quantityChange, Optional<Rate> rate, ValuationBasis basis) {
        this(code, quantityChange, rate, basis, Optional.empty());
    }

    /** A change priced at the bill rate. */
    public static VariationItem atBillRates(ItemCode code, Quantity change) {
        return new VariationItem(code, change, Optional.empty(), ValuationBasis.BILL_RATES);
    }

    /** A change priced at a rate of its own. */
    public static VariationItem priced(ItemCode code, Quantity change, Rate rate, ValuationBasis basis) {
        return new VariationItem(code, change, Optional.of(rate), basis);
    }

    /** A change priced pro rata: the rate of {@code from} in the bill in force, times {@code factor}. */
    public static VariationItem proRata(ItemCode code, Quantity change, ItemCode from, double factor) {
        return new VariationItem(
                code, change, Optional.empty(), ValuationBasis.PRO_RATA, Optional.of(new ProRata(from, factor)));
    }

    public VariationItem withCode(ItemCode other) {
        return new VariationItem(other, quantityChange, rate, basis, proRata);
    }

    public VariationItem withRate(Rate other) {
        return new VariationItem(code, quantityChange, Optional.ofNullable(other), basis, proRata);
    }

    public VariationItem withBasis(ValuationBasis other) {
        return new VariationItem(code, quantityChange, rate, other, proRata);
    }

    /** The rate this change is priced at against {@code bill}: its own, or the one it is built from. */
    public Optional<Rate> rateIn(Bill bill) {
        return rate.or(() -> proRata.map(source -> source.rateIn(bill)));
    }
}
