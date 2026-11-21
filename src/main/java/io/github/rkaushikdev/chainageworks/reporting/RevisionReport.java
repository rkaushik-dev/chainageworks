package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff;
import io.github.rkaushikdev.chainageworks.domain.bill.BillDiff.ChangeKind;
import io.github.rkaushikdev.chainageworks.domain.bill.BillHistory;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every revision of the bill against the one before it.
 *
 * <p>Stepwise rather than first against last. A rate that went up in Rev B and back down in Rev C
 * nets to nothing across the whole history, and it is exactly the movement somebody has to
 * explain, so the report that hides it is the wrong report.
 *
 * @param steps each revision against the one before it, oldest first
 * @param ratesMoved the steps that moved a rate, which is what a signatory has to be told
 */
public record RevisionReport(List<RevisionStep> steps, List<RevisionStep> ratesMoved) {

    public RevisionReport {
        steps = List.copyOf(steps);
        ratesMoved = List.copyOf(ratesMoved);
    }

    /**
     * One revision against the one before it.
     *
     * @param from the earlier revision
     * @param to the later revision
     * @param diff what moved between them
     */
    public record RevisionStep(BillRevision from, BillRevision to, BillDiff diff) {}

    /** The report for a bill history; a history of one revision has no steps. */
    public static RevisionReport build(BillHistory history) {
        List<BillRevision> revisions = history.revisions();
        List<RevisionStep> steps = new ArrayList<>();
        for (int i = 1; i < revisions.size(); i++) {
            BillRevision from = revisions.get(i - 1);
            BillRevision to = revisions.get(i);
            steps.add(new RevisionStep(from, to, BillDiff.between(from.bill(), to.bill())));
        }
        return new RevisionReport(
                steps,
                steps.stream().filter(step -> step.diff().hasRateChanges()).toList());
    }

    private static List<String> changeRow(BillDiff.Change change) {
        return List.of(
                change.code().format(),
                change.kind().wire().replace('_', ' '),
                change.before().map(item -> item.rate().format()).orElse(""),
                change.after().map(item -> item.rate().format()).orElse(""),
                change.before().map(item -> item.quantity().format()).orElse(""),
                change.after().map(item -> item.quantity().format()).orElse(""),
                change.delta().format());
    }

    /** The report as plain text: the counts, then every step with its changes. */
    public String render() {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("bill revisions"));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("steps", String.valueOf(steps.size())),
                Map.entry("steps that moved a rate", String.valueOf(ratesMoved.size())))));

        for (RevisionStep step : steps) {
            sections.add("");
            sections.add(Table.heading(
                    step.from().reference() + " to " + step.to().reference() + ", effective "
                            + SiteDates.format(step.to().effectiveFrom()),
                    "-"));
            sections.add(Table.pairs(List.of(
                    Map.entry("changes", String.valueOf(step.diff().changes().size())),
                    Map.entry("net movement", step.diff().netDelta().format()),
                    Map.entry("rates moved", step.diff().hasRateChanges() ? "yes" : "no"))));
            if (!step.diff().changes().isEmpty()) {
                sections.add(Table.render(
                        List.of(
                                Column.of("code"),
                                Column.of("change"),
                                Column.right("rate was"),
                                Column.right("rate now"),
                                Column.right("quantity was"),
                                Column.right("quantity now"),
                                Column.right("movement")),
                        step.diff().changes().stream()
                                .map(RevisionReport::changeRow)
                                .toList()));
            }
        }
        return String.join("\n", sections);
    }

    /**
     * The rate movements a certificate signer has to be told about.
     *
     * <p>Only rate changes, deliberately. A quantity change is remeasurement and it happens every
     * month; a rate change is somebody repricing the contract, and conflating the two buries the
     * second in four hundred instances of the first.
     */
    public List<String> rateChanges() {
        return ratesMoved.stream()
                .flatMap(step -> Stream.concat(
                                step.diff().ofKind(ChangeKind.RATE_CHANGED).stream(),
                                step.diff().ofKind(ChangeKind.RATE_AND_QUANTITY_CHANGED).stream())
                        .map(change ->
                                step.to().reference() + ": " + change.code().format() + " "
                                        + change.before()
                                                .map(item -> item.rate().format())
                                                .orElse("")
                                        + " to "
                                        + change.after()
                                                .map(item -> item.rate().format())
                                                .orElse("")))
                .toList();
    }
}
