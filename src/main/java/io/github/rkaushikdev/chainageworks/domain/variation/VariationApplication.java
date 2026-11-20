package io.github.rkaushikdev.chainageworks.domain.variation;

import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The bill as the variations in force at a date have changed it.
 *
 * @param bill the varied bill
 * @param applied the variations that were folded in, in instruction order
 * @param changes how each item moved against the unvaried bill
 * @param netValue what the changes are worth together
 */
public record VariationApplication(Bill bill, List<String> applied, List<BillDiff.Change> changes, Money netValue) {

    public VariationApplication {
        applied = List.copyOf(applied);
        changes = List.copyOf(changes);
    }

    /**
     * Fold the variations in force at a date into the bill.
     *
     * <p>A change priced at bill rates moves the quantity of an item that already exists. Anything
     * else brings its own rate, and if the bill has no item for it one is created, because a star
     * rate is by definition for work the bill never described.
     *
     * <p>Instructing a provisional item is the case worth naming. It does not change the quantity
     * or the rate at all: it changes the kind, and that is what makes the item earnable. Nothing
     * else in the system can turn provisional money into money somebody can claim.
     */
    public static VariationApplication apply(Bill bill, List<Variation> variations, LocalDate asAt) {
        List<Variation> inForce = Variation.effective(variations, asAt);
        Bill working = bill;
        for (Variation variation : inForce) {
            for (VariationItem item : variation.items()) {
                working = applyItem(working, variation, item);
            }
        }
        BillDiff diff = BillDiff.between(bill, working);
        return new VariationApplication(
                working, inForce.stream().map(Variation::id).toList(), diff.changes(), diff.netDelta());
    }

    /** What the variations in force are worth against the unvaried bill. */
    public static Money value(Bill bill, List<Variation> variations, LocalDate asAt) {
        return apply(bill, variations, asAt).netValue();
    }

    private static Bill applyItem(Bill bill, Variation variation, VariationItem change) {
        Optional<BillItem> existing = bill.find(change.code());
        if (change.basis() == ValuationBasis.BILL_RATES) {
            BillItem item = existing.orElseThrow(
                    () -> new NotFoundException("bill item", change.code().format()));
            return bill.withItem(requantified(item, change));
        }
        if (existing.isEmpty()) {
            return bill.withItem(createdFrom(variation, change));
        }
        return bill.withItem(repriced(existing.get(), change));
    }

    /** Instructing a provisional item is what makes it earnable. */
    private static BillItemKind nextKind(BillItemKind kind) {
        return kind == BillItemKind.PROVISIONAL ? BillItemKind.MEASURED : kind;
    }

    private static Quantity moved(BillItem item, VariationItem change) {
        Quantity quantity = item.quantity().plus(change.quantityChange());
        if (quantity.milli() < 0) {
            throw new ConflictException(
                    "a variation would take an item below nothing",
                    Details.of("code", item.code().format()));
        }
        return quantity;
    }

    private static BillItem requantified(BillItem item, VariationItem change) {
        return BillItem.build(new BillItemInput(
                item.code(),
                item.description(),
                item.rate(),
                moved(item, change),
                nextKind(item.kind()),
                item.chainage()));
    }

    private static BillItem repriced(BillItem item, VariationItem change) {
        Rate rate = change.rate().orElse(item.rate());
        BillItemKind kind = nextKind(item.kind() == BillItemKind.MEASURED ? BillItemKind.STAR : item.kind());
        return BillItem.build(
                new BillItemInput(item.code(), item.description(), rate, moved(item, change), kind, item.chainage()));
    }

    private static BillItem createdFrom(Variation variation, VariationItem change) {
        Rate rate = change.rate()
                .orElseThrow(() -> new ConflictException(
                        "a new variation item needs a rate",
                        Details.of(
                                "variation",
                                variation.id(),
                                "code",
                                change.code().format())));
        if (change.quantityChange().milli() < 0) {
            throw new ConflictException(
                    "a variation cannot omit an item the bill does not carry",
                    Details.of(
                            "variation", variation.id(), "code", change.code().format()));
        }
        boolean measurable = change.quantityChange().unit().isMeasurable();
        return BillItem.build(new BillItemInput(
                change.code(),
                variation.title() + " (" + variation.reference() + ")",
                rate,
                change.quantityChange(),
                measurable ? BillItemKind.STAR : BillItemKind.LUMP,
                variation.extent()));
    }
}
