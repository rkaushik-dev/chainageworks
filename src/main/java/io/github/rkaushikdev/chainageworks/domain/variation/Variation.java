package io.github.rkaushikdev.chainageworks.domain.variation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A change to the works the engineer has proposed, instructed or agreed, and the bill items it
 * moves.
 *
 * @param id the variation's key
 * @param reference the engineer's instruction it arises from
 * @param title what it is
 * @param status how far along it is
 * @param instructedOn the day it was instructed
 * @param agreedOn the day its price was agreed
 * @param items the bill items it changes
 * @param extent where on the alignment it applies
 */
public record Variation(
        String id,
        String reference,
        String title,
        VariationStatus status,
        Optional<LocalDate> instructedOn,
        Optional<LocalDate> agreedOn,
        List<VariationItem> items,
        Optional<ChainageRange> extent) {

    public Variation {
        instructedOn = instructedOn == null ? Optional.empty() : instructedOn;
        agreedOn = agreedOn == null ? Optional.empty() : agreedOn;
        items = List.copyOf(items);
        extent = extent == null ? Optional.empty() : extent;
    }

    /**
     * Build a variation, refusing missing instruction and agreement dates, agreement before
     * instruction, a variation that changes nothing, one item touched twice, and rates that do not
     * match the basis or the unit.
     */
    public static Variation build(VariationInput input) {
        VariationStatus status = input.status() == null ? VariationStatus.PROPOSED : input.status();
        Optional<LocalDate> instructedOn = Optional.ofNullable(input.instructedOn());
        Optional<LocalDate> agreedOn = Optional.ofNullable(input.agreedOn());

        if ((status == VariationStatus.INSTRUCTED || status == VariationStatus.AGREED) && instructedOn.isEmpty()) {
            throw new BadInputException(
                    "an instructed variation needs the date it was instructed",
                    Details.of("variation", input.id(), "status", status.wire()));
        }
        if (status == VariationStatus.AGREED && agreedOn.isEmpty()) {
            throw new BadInputException(
                    "an agreed variation needs the date it was agreed", Details.of("variation", input.id()));
        }
        if (agreedOn.isPresent() && instructedOn.isPresent() && agreedOn.get().isBefore(instructedOn.get())) {
            throw new BadInputException(
                    "a variation cannot be agreed before it was instructed",
                    Details.of(
                            "variation", input.id(),
                            "instructed", SiteDates.format(instructedOn.get()),
                            "agreed", SiteDates.format(agreedOn.get())));
        }
        if (input.items().isEmpty()) {
            throw new BadInputException(
                    "a variation that changes nothing is not a variation", Details.of("variation", input.id()));
        }
        Set<String> seen = new HashSet<>();
        for (VariationItem item : input.items()) {
            String code = item.code().format();
            if (!seen.add(item.code().key())) {
                throw new BadInputException(
                        "a variation touches the same item twice", Details.of("variation", input.id(), "code", code));
            }
            if (item.basis() == ValuationBasis.BILL_RATES && item.rate().isPresent()) {
                throw new BadInputException(
                        "an item priced at bill rates cannot carry a rate of its own",
                        Details.of("variation", input.id(), "code", code));
            }
            if (item.basis() != ValuationBasis.BILL_RATES && item.rate().isEmpty()) {
                throw new BadInputException(
                        "an item not priced at bill rates needs its own rate",
                        Details.of(
                                "variation",
                                input.id(),
                                "code",
                                code,
                                "basis",
                                item.basis().wire()));
            }
            if (item.rate().isPresent()
                    && item.rate().get().unit() != item.quantityChange().unit()) {
                throw new BadInputException(
                        "the variation rate and quantity are in different units",
                        Details.of("variation", input.id(), "code", code));
            }
        }
        return new Variation(
                input.id(),
                input.reference(),
                input.title(),
                status,
                instructedOn,
                agreedOn,
                input.items(),
                Optional.ofNullable(input.extent()));
    }

    /**
     * Whether the variation has reached the works at a date.
     *
     * <p>The instruction date is what counts, not the agreement date. Work ordered in March and
     * priced in June was still built in March, and valuing it from June would leave three
     * certificates short.
     */
    public boolean isEffectiveAt(LocalDate asAt) {
        if (status != VariationStatus.INSTRUCTED && status != VariationStatus.AGREED) {
            return false;
        }
        return instructedOn.map(on -> !on.isAfter(asAt)).orElse(false);
    }

    public boolean isAgreed() {
        return status == VariationStatus.AGREED;
    }

    /** Instructed but not yet agreed, which is what the risk register wants. */
    public boolean isUnagreed() {
        return status == VariationStatus.INSTRUCTED;
    }

    /** The variations in force at {@code asAt}, in the order they were instructed. */
    public static List<Variation> effective(List<Variation> variations, LocalDate asAt) {
        List<Variation> out = new ArrayList<>(
                variations.stream().filter(v -> v.isEffectiveAt(asAt)).toList());
        out.sort((a, b) -> {
            long left = a.instructedOn().map(LocalDate::toEpochDay).orElse(0L);
            long right = b.instructedOn().map(LocalDate::toEpochDay).orElse(0L);
            int byDate = Long.compare(left, right);
            if (byDate != 0) {
                return byDate;
            }
            return a.id().compareTo(b.id()) < 0 ? -1 : 1;
        });
        return List.copyOf(out);
    }

    /** The change this variation makes to {@code code}, if any. */
    public Optional<VariationItem> itemFor(ItemCode code) {
        String key = code.key();
        return items.stream().filter(i -> i.code().key().equals(key)).findFirst();
    }

    /** {@code VO-012 title (EI-045, instructed 2025-03-14)}, like a variation register line. */
    public String describe() {
        String when =
                instructedOn.map(on -> "instructed " + SiteDates.format(on)).orElse("not instructed");
        return id + " " + title + " (" + reference + ", " + when + ")";
    }
}
