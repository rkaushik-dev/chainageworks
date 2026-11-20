package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.util.Optional;

/**
 * What a bill item is built from. The kind defaults to measured and the chainage to none.
 *
 * @param code the item code
 * @param description what the bill says the work is
 * @param rate the tendered rate
 * @param quantity the quantity the bill was priced on
 * @param kind what kind of money it is
 * @param chainage where on the alignment the item applies, when the bill says
 */
public record BillItemInput(
        ItemCode code,
        String description,
        Rate rate,
        Quantity quantity,
        BillItemKind kind,
        Optional<ChainageRange> chainage) {

    public BillItemInput {
        kind = kind == null ? BillItemKind.MEASURED : kind;
        chainage = chainage == null ? Optional.empty() : chainage;
    }

    /** A measured item with no chainage. */
    public static BillItemInput of(ItemCode code, String description, Rate rate, Quantity quantity) {
        return new BillItemInput(code, description, rate, quantity, BillItemKind.MEASURED, Optional.empty());
    }

    public BillItemInput withKind(BillItemKind other) {
        return new BillItemInput(code, description, rate, quantity, other, chainage);
    }

    public BillItemInput withChainage(ChainageRange range) {
        return new BillItemInput(code, description, rate, quantity, kind, Optional.ofNullable(range));
    }
}
