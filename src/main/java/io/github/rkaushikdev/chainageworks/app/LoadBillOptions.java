package io.github.rkaushikdev.chainageworks.app;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What a priced bill sheet becomes when it is loaded: the revision it is filed as and the day that
 * revision takes effect.
 *
 * @param reference the revision's key, {@code Rev B}; it has to be new to the job
 * @param effectiveFrom the first day the revision's rates apply
 * @param note why the revision was issued; null means none
 * @param partial apply the rows that parsed even when some did not
 */
public record LoadBillOptions(String reference, LocalDate effectiveFrom, String note, boolean partial) {

    public LoadBillOptions {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        note = note == null ? "" : note;
    }

    /** A revision with no note, applied only when every row of the sheet parsed. */
    public static LoadBillOptions of(String reference, LocalDate effectiveFrom) {
        return new LoadBillOptions(reference, effectiveFrom, null, false);
    }

    public LoadBillOptions withNote(String other) {
        return new LoadBillOptions(reference, effectiveFrom, other, partial);
    }

    public LoadBillOptions withPartial(boolean other) {
        return new LoadBillOptions(reference, effectiveFrom, note, other);
    }
}
