package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationApplication;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationItem;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationStatus;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The variation register, valued one order at a time and then together.
 *
 * <p>Each order is valued on its own against the unvaried bill rather than incrementally against
 * the running total. Two orders that both move the same item would otherwise report a value that
 * depends on the order they happen to be listed in, and a register that reorders itself when
 * somebody sorts a column is a register nobody trusts.
 *
 * @param asAt the date the register is valued at
 * @param lines one line per order, in register order
 * @param inForce everything in force at the date, valued together
 * @param unagreed in force but nobody has agreed the price yet
 * @param proposed orders proposed and not instructed. A count rather than a value on purpose: an
 *     order nobody has instructed is worth nothing under the contract, and putting a figure against
 *     it invites somebody to add it to the forecast.
 */
public record VariationReport(LocalDate asAt, List<VariationLine> lines, Money inForce, Money unagreed, int proposed) {

    public VariationReport {
        lines = List.copyOf(lines);
    }

    /**
     * One order on the register.
     *
     * @param variation the order
     * @param value what this one alone is worth against the unvaried bill
     * @param inForce whether it is in force at the date
     * @param agreed whether its price has been agreed
     * @param bases the ways it is priced, which is what the argument is about, each once
     */
    public record VariationLine(
            Variation variation, Money value, boolean inForce, boolean agreed, List<ValuationBasis> bases) {

        public VariationLine {
            bases = List.copyOf(bases);
        }
    }

    /** The register of {@code variations} against {@code bill}, valued at {@code asAt}. */
    public static VariationReport build(Bill bill, List<Variation> variations, LocalDate asAt) {
        Currency currency = bill.currency();
        List<VariationLine> lines = variations.stream()
                .map(variation -> {
                    boolean inForce = variation.isEffectiveAt(asAt);
                    return new VariationLine(
                            variation,
                            inForce
                                    ? VariationApplication.apply(bill, List.of(variation), asAt)
                                            .netValue()
                                    : Money.zero(currency),
                            inForce,
                            variation.agreedOn().isPresent(),
                            variation.items().stream()
                                    .map(VariationItem::basis)
                                    .distinct()
                                    .toList());
                })
                .toList();

        List<VariationLine> live = lines.stream().filter(VariationLine::inForce).toList();
        return new VariationReport(
                asAt,
                lines,
                VariationApplication.apply(
                                bill,
                                live.stream().map(VariationLine::variation).toList(),
                                asAt)
                        .netValue(),
                Money.sum(
                        live.stream()
                                .filter(line -> line.variation().isUnagreed())
                                .map(VariationLine::value)
                                .toList(),
                        currency),
                (int) lines.stream()
                        .filter(line -> !line.inForce() && line.variation().status() == VariationStatus.PROPOSED)
                        .count());
    }

    /** The register as plain text: the counts and values, then one line per order. */
    public String render() {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("variations at " + SiteDates.format(asAt)));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("orders on the register", String.valueOf(lines.size())),
                Map.entry(
                        "in force",
                        String.valueOf(
                                lines.stream().filter(VariationLine::inForce).count())),
                Map.entry("value in force", inForce.format()),
                Map.entry("not yet agreed", unagreed.format()),
                Map.entry("proposed, not instructed", String.valueOf(proposed)))));

        if (!lines.isEmpty()) {
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("order"),
                            Column.of("title").withWidth(34),
                            Column.of("status"),
                            Column.of("where").withWidth(24),
                            Column.of("basis"),
                            Column.right("value")),
                    lines.stream()
                            .map(line -> List.of(
                                    line.variation().reference(),
                                    line.variation().title(),
                                    line.variation().status().wire(),
                                    line.variation()
                                            .extent()
                                            .map(ChainageRange::format)
                                            .orElse(""),
                                    line.bases().stream()
                                            .map(ValuationBasis::wire)
                                            .collect(Collectors.joining(", ")),
                                    line.value().format()))
                            .toList()));
        }
        return String.join("\n", sections);
    }

    /** The orders in force that nobody has agreed a price for, dearest first. */
    public List<String> chaseAgreement() {
        return lines.stream()
                .filter(line -> line.inForce() && !line.agreed())
                .sorted(Comparator.comparingLong(
                                (VariationLine line) -> Math.abs(line.value().minor()))
                        .reversed())
                .map(line -> line.variation().reference() + " "
                        + line.variation().title() + ", " + line.value().format() + " not agreed")
                .toList();
    }

    /** Every item an order in force touches, for the surveyor remeasuring it, once each and in order. */
    public List<String> touchedItems() {
        Set<String> out = new TreeSet<>();
        for (VariationLine line : lines) {
            if (!line.inForce()) {
                continue;
            }
            for (VariationItem item : line.variation().items()) {
                out.add(item.code().format() + " " + item.quantityChange().format());
            }
        }
        return List.copyOf(out);
    }
}
