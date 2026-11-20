package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A priced bill of quantities.
 *
 * <p>Held sorted, because everything downstream reads it in bill order and a bill that arrives in
 * spreadsheet order would put the rollups in the wrong place. Held immutable, because the revision
 * history is what decides which rates apply on a given date, and a bill that can be edited in place
 * has no history.
 *
 * @param id the bill's key
 * @param title what the bill is called
 * @param currency what every item is priced in
 * @param items the items, in bill order
 */
public record Bill(String id, String title, Currency currency, List<BillItem> items) {

    public Bill {
        items = List.copyOf(items);
    }

    /**
     * One line per work class for the summary page of a bill.
     *
     * @param workClass the class letter
     * @param name the class name
     * @param items how many items the class carries
     * @param value what they are billed at
     */
    public record ClassRollup(String workClass, String name, int items, Money value) {}

    /** Build a bill, refusing a code that appears twice and an item in another currency. */
    public static Bill build(String id, String title, Currency currency, List<BillItem> items) {
        Set<String> seen = new HashSet<>();
        for (BillItem item : items) {
            String key = item.code().key();
            if (!seen.add(key)) {
                throw new BadInputException(
                        "the same item code appears twice in one bill",
                        Details.of("bill", id, "code", item.code().format()));
            }
            if (item.rate().currency() != currency) {
                throw new BadInputException(
                        "an item is priced in a different currency from the bill",
                        Details.of(
                                "bill", id,
                                "code", item.code().format(),
                                "billCurrency", currency.code(),
                                "itemCurrency", item.rate().currency().code()));
            }
        }
        List<BillItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> a.code().compareTo(b.code()));
        return new Bill(id, title, currency, sorted);
    }

    /** A bill with no items yet. */
    public static Bill empty(String id, String title, Currency currency) {
        return new Bill(id, title, currency, List.of());
    }

    public int itemCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Nothing, in the bill's currency. */
    public Money zero() {
        return Money.zero(currency);
    }

    /** The item with exactly this code. A heading never matches an item under it. */
    public Optional<BillItem> find(ItemCode code) {
        return items.stream().filter(item -> item.code().equals(code)).findFirst();
    }

    /** The item with this code, or a {@link NotFoundException}. */
    public BillItem require(ItemCode code) {
        return find(code).orElseThrow(() -> new NotFoundException("bill item", code.format()));
    }

    public boolean has(ItemCode code) {
        return find(code).isPresent();
    }

    /** Every item that sits under a heading, in bill order. */
    public List<BillItem> itemsUnder(ItemCode heading) {
        return items.stream().filter(item -> heading.isAncestorOf(item.code())).toList();
    }

    public Money total() {
        return Money.sum(items.stream().map(BillItem::billedValue).toList(), currency);
    }

    /** What is actually earnable: the total less the provisional and prime cost. */
    public Money measuredTotal() {
        return Money.sum(
                items.stream()
                        .filter(BillItem::isEarnable)
                        .map(BillItem::billedValue)
                        .toList(),
                currency);
    }

    public Money provisionalTotal() {
        return Money.sum(
                items.stream()
                        .filter(BillItem::isProvisional)
                        .map(BillItem::billedValue)
                        .toList(),
                currency);
    }

    public Money totalUnder(ItemCode heading) {
        return Money.sum(itemsUnder(heading).stream().map(BillItem::billedValue).toList(), currency);
    }

    /** One line per work class, in class order, for the summary page of a bill. */
    public List<ClassRollup> rollupByClass() {
        Map<String, List<BillItem>> byClass = new TreeMap<>();
        for (BillItem item : items) {
            byClass.computeIfAbsent(item.code().workClass(), k -> new ArrayList<>())
                    .add(item);
        }
        List<ClassRollup> out = new ArrayList<>();
        byClass.forEach((workClass, classItems) -> out.add(new ClassRollup(
                workClass,
                classItems.get(0).code().workClassName(),
                classItems.size(),
                Money.sum(classItems.stream().map(BillItem::billedValue).toList(), currency))));
        return List.copyOf(out);
    }

    /** A new bill with the item added, or replacing one with the same code. */
    public Bill withItem(BillItem item) {
        List<BillItem> kept = new ArrayList<>(items.stream()
                .filter(existing -> !existing.code().equals(item.code()))
                .toList());
        kept.add(item);
        return build(id, title, currency, kept);
    }

    /** A new bill without the item, refusing a code the bill does not carry. */
    public Bill withoutItem(ItemCode code) {
        List<BillItem> kept =
                items.stream().filter(existing -> !existing.code().equals(code)).toList();
        if (kept.size() == items.size()) {
            throw new NotFoundException("bill item", code.format());
        }
        return build(id, title, currency, kept);
    }
}
