package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.Optional;

/**
 * One line of a priced bill: the code, what the work is, the rate and the quantity it was priced
 * on.
 *
 * @param code the item code
 * @param description what the bill says the work is
 * @param unit the unit of the billed quantity
 * @param rate the tendered rate
 * @param quantity the quantity the bill was priced on, not what has been built
 * @param kind what kind of money it is
 * @param chainage where on the alignment the item applies, when the bill says
 */
public record BillItem(
        ItemCode code,
        String description,
        Unit unit,
        Rate rate,
        Quantity quantity,
        BillItemKind kind,
        Optional<ChainageRange> chainage) {

    public BillItem {
        chainage = chainage == null ? Optional.empty() : chainage;
    }

    /**
     * Build an item, refusing a rate and quantity in different units, a blank description, a
     * negative quantity, a lump item not billed as a sum, and a sum item that is neither a lump nor
     * a prime cost.
     */
    public static BillItem build(BillItemInput input) {
        BillItemKind kind = input.kind();
        String code = input.code().format();
        if (input.rate().unit() != input.quantity().unit()) {
            throw new BadInputException(
                    "the rate and the billed quantity are in different units",
                    Details.of(
                            "code",
                            code,
                            "rate",
                            input.rate().unit().code(),
                            "quantity",
                            input.quantity().unit().code()));
        }
        if (Text.trim(input.description()).isEmpty()) {
            throw new BadInputException("a bill item needs a description", Details.of("code", code));
        }
        if (input.quantity().milli() < 0) {
            throw new BadInputException(
                    "a billed quantity cannot be negative",
                    Details.of("code", code, "quantity", input.quantity().format()));
        }
        if (kind == BillItemKind.LUMP && input.quantity().unit().isMeasurable()) {
            throw new BadInputException(
                    "a lump item is billed as a sum",
                    Details.of("code", code, "unit", input.quantity().unit().code()));
        }
        if (!input.quantity().unit().isMeasurable() && kind != BillItemKind.LUMP && kind != BillItemKind.PRIME_COST) {
            throw new BadInputException(
                    "a sum item has to be a lump or a prime cost item", Details.of("code", code, "kind", kind.wire()));
        }
        return new BillItem(
                input.code(),
                Text.trim(input.description()),
                input.quantity().unit(),
                input.rate(),
                input.quantity(),
                kind,
                input.chainage());
    }

    /** What the bill says the item is worth at the billed quantity. */
    public Money billedValue() {
        return rate.extend(quantity);
    }

    public boolean isMeasured() {
        return kind == BillItemKind.MEASURED || kind == BillItemKind.STAR;
    }

    /** Provisional and prime cost money is in the total but is not yet earnable. */
    public boolean isProvisional() {
        return kind == BillItemKind.PROVISIONAL || kind == BillItemKind.PRIME_COST;
    }

    public boolean isLump() {
        return kind == BillItemKind.LUMP;
    }

    public boolean isNilQuantity() {
        return quantity.isZero();
    }

    /**
     * Whether an item can be claimed against at all. A provisional item cannot, until an
     * instruction turns it into a measured one, and that instruction is a variation rather than an
     * edit of the bill.
     */
    public boolean isEarnable() {
        return !isProvisional();
    }

    /** {@code E423.1 excavation [12+000.000 to 12+800.000] 12400.00 m3 at £4.85/m3}. */
    public String describe() {
        String where = chainage.map(range -> " [" + range.format() + "]").orElse("");
        return code.format() + " " + description + where + " " + quantity.format() + " at " + rate.format();
    }

    /** A copy of the item with a different billed quantity, used by variations. */
    public BillItem reQuantify(double amount) {
        return build(new BillItemInput(code, description, rate, Quantity.of(amount, unit), kind, chainage));
    }
}
