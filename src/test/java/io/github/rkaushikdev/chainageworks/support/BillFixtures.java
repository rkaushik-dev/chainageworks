package io.github.rkaushikdev.chainageworks.support;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import java.util.List;
import java.util.Optional;

/** Bill items and bills for tests, priced in sterling. */
public final class BillFixtures {

    private BillFixtures() {}

    /**
     * One line of a test bill.
     *
     * @param code the item code as written
     * @param description what the item is, or empty for {@code item <code>}
     * @param rate the rate in pounds
     * @param quantity the billed amount
     * @param unit the unit both are in
     * @param kind what kind of money it is
     */
    public record ItemSpec(
            String code, Optional<String> description, double rate, double quantity, Unit unit, BillItemKind kind) {

        public static ItemSpec of(String code, double rate, double quantity, Unit unit) {
            return new ItemSpec(code, Optional.empty(), rate, quantity, unit, BillItemKind.MEASURED);
        }

        public ItemSpec described(String text) {
            return new ItemSpec(code, Optional.of(text), rate, quantity, unit, kind);
        }

        public ItemSpec kind(BillItemKind other) {
            return new ItemSpec(code, description, rate, quantity, unit, other);
        }
    }

    public static BillItem item(ItemSpec spec) {
        return BillItem.build(new BillItemInput(
                ItemCode.parse(spec.code()),
                spec.description().orElse("item " + spec.code()),
                Rate.of(spec.rate(), Currency.GBP, spec.unit()),
                Quantity.of(spec.quantity(), spec.unit()),
                spec.kind(),
                Optional.empty()));
    }

    public static Bill bill(List<ItemSpec> specs) {
        return bill(specs, "B1");
    }

    public static Bill bill(List<ItemSpec> specs, String id) {
        return Bill.build(
                id,
                "A1 improvement, bill 1",
                Currency.GBP,
                specs.stream().map(BillFixtures::item).toList());
    }

    /**
     * A small but realistic bill: earthworks, concrete and roads, with one provisional item and
     * one lump sum, so every code path has a case.
     */
    public static final List<ItemSpec> SAMPLE_SPECS = List.of(
            ItemSpec.of("A213.1", 48_500, 1, Unit.SUM)
                    .described("establish and remove site accommodation")
                    .kind(BillItemKind.LUMP),
            ItemSpec.of("E423.1", 4.85, 12_400, Unit.M3).described("excavation for cuttings, maximum depth 2 to 5 m"),
            ItemSpec.of("E423.2", 5.4, 3200, Unit.M3).described("excavation for cuttings, maximum depth 5 to 10 m"),
            ItemSpec.of("E532.1", 3.15, 9800, Unit.M3).described("filling to embankments, selected excavated material"),
            ItemSpec.of("E423.9", 28, 500, Unit.M3)
                    .described("rock excavation, provisional")
                    .kind(BillItemKind.PROVISIONAL),
            ItemSpec.of("F624.1", 32.5, 640, Unit.M2).described("formwork, plane vertical, width over 1.22 m"),
            ItemSpec.of("R712.1", 11.4, 4000, Unit.M2).described("sub base, depth 225 mm"),
            ItemSpec.of("R712.2", 18.75, 4000, Unit.M2).described("base, dense bitumen macadam, depth 120 mm"));

    public static Bill sampleBill() {
        return bill(SAMPLE_SPECS);
    }
}
