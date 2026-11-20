package io.github.rkaushikdev.chainageworks.domain.variation;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;
import java.util.List;

/**
 * What a variation is built from. A {@code null} status means proposed; {@code null} dates and
 * extent mean none.
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
public record VariationInput(
        String id,
        String reference,
        String title,
        VariationStatus status,
        LocalDate instructedOn,
        LocalDate agreedOn,
        List<VariationItem> items,
        ChainageRange extent) {

    public VariationInput {
        items = List.copyOf(items);
    }

    /** A proposal with no dates and no extent. */
    public static VariationInput of(String id, String reference, String title, List<VariationItem> items) {
        return new VariationInput(id, reference, title, null, null, null, items, null);
    }

    public VariationInput withId(String other) {
        return new VariationInput(other, reference, title, status, instructedOn, agreedOn, items, extent);
    }

    public VariationInput withStatus(VariationStatus other) {
        return new VariationInput(id, reference, title, other, instructedOn, agreedOn, items, extent);
    }

    public VariationInput withInstructedOn(LocalDate other) {
        return new VariationInput(id, reference, title, status, other, agreedOn, items, extent);
    }

    public VariationInput withAgreedOn(LocalDate other) {
        return new VariationInput(id, reference, title, status, instructedOn, other, items, extent);
    }

    public VariationInput withItems(List<VariationItem> other) {
        return new VariationInput(id, reference, title, status, instructedOn, agreedOn, other, extent);
    }

    public VariationInput withExtent(ChainageRange other) {
        return new VariationInput(id, reference, title, status, instructedOn, agreedOn, items, other);
    }
}
