package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What one revision of the bill did to the last one.
 *
 * <p>The valuation needs this for two reasons. A rate change has to be flagged to whoever signs the
 * certificate, and a quantity change is what tells the variation account whether the movement was
 * priced or measured.
 *
 * @param changes every item that moved, in bill order
 * @param netDelta what all of them are worth together
 */
public record BillDiff(List<Change> changes, Money netDelta) {

    public BillDiff {
        changes = List.copyOf(changes);
    }

    /** How an item moved between two bills. */
    public enum ChangeKind {
        ADDED("added"),
        REMOVED("removed"),
        RATE_CHANGED("rate_changed"),
        QUANTITY_CHANGED("quantity_changed"),
        RATE_AND_QUANTITY_CHANGED("rate_and_quantity_changed"),
        DESCRIPTION_CHANGED("description_changed"),
        KIND_CHANGED("kind_changed");

        private final String wire;

        ChangeKind(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        @Override
        public String toString() {
            return wire;
        }
    }

    /**
     * One item that moved.
     *
     * @param code the item code
     * @param kind how it moved
     * @param before the item in the earlier bill, absent for an addition
     * @param after the item in the later bill, absent for a removal
     * @param delta what the change is worth: the value after less the value before
     */
    public record Change(
            ItemCode code, ChangeKind kind, Optional<BillItem> before, Optional<BillItem> after, Money delta) {

        /** {@code E423.1 rate and quantity changed}. */
        public String describe() {
            return code.format() + " " + kind.wire().replace('_', ' ');
        }
    }

    /** The changes from {@code before} to {@code after}, refusing two bills in different currencies. */
    public static BillDiff between(Bill before, Bill after) {
        if (before.currency() != after.currency()) {
            throw new BadInputException(
                    "cannot compare bills in different currencies",
                    Details.of(
                            "before",
                            before.currency().code(),
                            "after",
                            after.currency().code()));
        }
        Currency currency = before.currency();
        List<Change> changes = new ArrayList<>();

        for (BillItem item : before.items()) {
            Optional<BillItem> now = after.find(item.code());
            if (now.isEmpty()) {
                changes.add(new Change(
                        item.code(),
                        ChangeKind.REMOVED,
                        Optional.of(item),
                        Optional.empty(),
                        Money.zero(currency).minus(item.billedValue())));
                continue;
            }
            Optional<ChangeKind> kind = classify(item, now.get());
            if (kind.isEmpty()) {
                continue;
            }
            changes.add(new Change(
                    item.code(),
                    kind.get(),
                    Optional.of(item),
                    now,
                    now.get().billedValue().minus(item.billedValue())));
        }

        Set<String> beforeKeys = new HashSet<>();
        before.items().forEach(i -> beforeKeys.add(i.code().key()));
        for (BillItem item : after.items()) {
            if (beforeKeys.contains(item.code().key())) {
                continue;
            }
            changes.add(
                    new Change(item.code(), ChangeKind.ADDED, Optional.empty(), Optional.of(item), item.billedValue()));
        }

        changes.sort((a, b) -> a.code().compareTo(b.code()));
        return new BillDiff(
                changes, Money.sum(changes.stream().map(Change::delta).toList(), currency));
    }

    /**
     * A rate change and a quantity change together get their own name rather than being reported
     * twice, because a reader who sees two lines for one item assumes the item moved twice.
     */
    private static Optional<ChangeKind> classify(BillItem before, BillItem after) {
        boolean rateMoved = !before.rate().equals(after.rate());
        boolean quantityMoved = !before.quantity().equals(after.quantity());
        if (rateMoved && quantityMoved) {
            return Optional.of(ChangeKind.RATE_AND_QUANTITY_CHANGED);
        }
        if (rateMoved) {
            return Optional.of(ChangeKind.RATE_CHANGED);
        }
        if (quantityMoved) {
            return Optional.of(ChangeKind.QUANTITY_CHANGED);
        }
        if (before.kind() != after.kind()) {
            return Optional.of(ChangeKind.KIND_CHANGED);
        }
        if (!before.description().equals(after.description())) {
            return Optional.of(ChangeKind.DESCRIPTION_CHANGED);
        }
        return Optional.empty();
    }

    public List<Change> ofKind(ChangeKind kind) {
        return changes.stream().filter(c -> c.kind() == kind).toList();
    }

    public boolean hasRateChanges() {
        return changes.stream()
                .anyMatch(c -> c.kind() == ChangeKind.RATE_CHANGED || c.kind() == ChangeKind.RATE_AND_QUANTITY_CHANGED);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
