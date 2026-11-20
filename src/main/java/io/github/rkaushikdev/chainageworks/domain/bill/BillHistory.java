package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.time.DateRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every revision of the bill, in the order they took effect, and which one was in force on a
 * given day.
 *
 * @param revisions the revisions, earliest first
 */
public record BillHistory(List<BillRevision> revisions) {

    public BillHistory {
        revisions = List.copyOf(revisions);
    }

    /**
     * Build a history, refusing an empty one, two revisions taking effect on one day and two
     * revisions sharing a reference.
     */
    public static BillHistory build(List<BillRevision> revisions) {
        if (revisions.isEmpty()) {
            throw new BadInputException("a bill history needs at least one revision");
        }
        List<BillRevision> sorted = new ArrayList<>(revisions);
        sorted.sort(Comparator.comparing(BillRevision::effectiveFrom));
        for (int i = 1; i < sorted.size(); i += 1) {
            BillRevision previous = sorted.get(i - 1);
            BillRevision current = sorted.get(i);
            if (previous.effectiveFrom().equals(current.effectiveFrom())) {
                throw new BadInputException(
                        "two bill revisions take effect on the same day",
                        Details.of(
                                "date", SiteDates.format(current.effectiveFrom()),
                                "first", previous.reference(),
                                "second", current.reference()));
            }
        }
        Set<String> seen = new HashSet<>();
        for (BillRevision revision : sorted) {
            if (!seen.add(revision.reference())) {
                throw new BadInputException(
                        "two bill revisions share a reference", Details.of("reference", revision.reference()));
            }
        }
        return new BillHistory(sorted);
    }

    public BillRevision first() {
        if (revisions.isEmpty()) {
            throw new BadInputException("a bill history needs at least one revision");
        }
        return revisions.get(0);
    }

    public BillRevision latest() {
        if (revisions.isEmpty()) {
            throw new BadInputException("a bill history needs at least one revision");
        }
        return revisions.get(revisions.size() - 1);
    }

    /**
     * The revision in force on a date: the latest one that had taken effect by then. A date before
     * the first revision has no bill at all, which is an error rather than an empty bill, because
     * valuing against an empty bill would report a total of nothing instead of refusing.
     */
    public BillRevision asAt(LocalDate date) {
        BillRevision found = null;
        for (BillRevision revision : revisions) {
            if (SiteDates.compare(revision.effectiveFrom(), date) <= 0) {
                found = revision;
            } else {
                break;
            }
        }
        if (found == null) {
            throw new BadInputException(
                    "no bill revision was in force on that date",
                    Details.of(
                            "date", SiteDates.format(date),
                            "firstRevision", SiteDates.format(first().effectiveFrom())));
        }
        return found;
    }

    public Bill billAsAt(LocalDate date) {
        return asAt(date).bill();
    }

    public BillItem itemAsAt(LocalDate date, ItemCode code) {
        return billAsAt(date).find(code).orElseThrow(() -> new NotFoundException("bill item", code.format()));
    }

    /** The rate that values work measured on a given day. */
    public Rate rateAsAt(LocalDate date, ItemCode code) {
        return itemAsAt(date, code).rate();
    }

    public boolean hasRevisionAsAt(LocalDate date) {
        return SiteDates.compare(first().effectiveFrom(), date) <= 0;
    }

    /** Revisions that take effect inside a period, which a valuation has to flag. */
    public List<BillRevision> revisionsIn(DateRange range) {
        return revisions.stream().filter(r -> range.contains(r.effectiveFrom())).toList();
    }

    public int count() {
        return revisions.size();
    }

    public BillRevision find(String reference) {
        return revisions.stream()
                .filter(r -> r.reference().equals(reference))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("bill revision", reference));
    }

    /** A new history with the revision added. This one is left alone. */
    public BillHistory withRevision(BillRevision revision) {
        List<BillRevision> all = new ArrayList<>(revisions);
        all.add(revision);
        return build(all);
    }
}
