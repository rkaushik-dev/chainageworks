package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;

/**
 * One issue of the bill, with the date it takes effect.
 *
 * <p>A bill is not edited. Every change is a new revision with its own effective date, because a
 * valuation has to be priced against the revision that was in force at its cut-off, not against
 * whatever the bill says today. Repricing last month's certificate because a rate changed this
 * month is the single most common way an interim valuation goes wrong.
 *
 * @param reference the revision's name on the transmittal, {@code Rev B}
 * @param effectiveFrom the first day it is in force
 * @param bill the bill as issued
 * @param note why it was issued
 */
public record BillRevision(String reference, LocalDate effectiveFrom, Bill bill, String note) {

    /** {@code Rev A from 2025-01-01: contract bill at award}, like a transmittal line. */
    public String describe() {
        return reference + " from " + SiteDates.format(effectiveFrom) + ": " + note;
    }

    public BillRevision withEffectiveFrom(LocalDate date) {
        return new BillRevision(reference, date, bill, note);
    }

    public BillRevision withReference(String other) {
        return new BillRevision(other, effectiveFrom, bill, note);
    }
}
