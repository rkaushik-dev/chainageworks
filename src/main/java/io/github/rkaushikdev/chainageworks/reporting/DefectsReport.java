package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.defects.CertificateBlock;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectSeverity;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The defects position at a date.
 *
 * <p>Built around what is stopping the certificate rather than around the count, because a list of
 * forty minor items and a list of one safety item read the same on a count and mean completely
 * different things.
 *
 * @param asAt the date the position is taken at
 * @param periodEnds the day the defects period ends, after any extension for works out of use
 * @param extendedBy calendar days the period was extended by because defects kept the works out of
 *     use
 * @param summary the list counted by state and severity
 * @param outstanding the open defects, soonest due first
 * @param overdue the open defects past their due date
 * @param block what is stopping the defects certificate, empty when it can be issued
 */
public record DefectsReport(
        LocalDate asAt,
        LocalDate periodEnds,
        int extendedBy,
        Defect.Summary summary,
        List<Defect> outstanding,
        List<Defect> overdue,
        Optional<CertificateBlock> block) {

    public DefectsReport {
        outstanding = List.copyOf(outstanding);
        overdue = List.copyOf(overdue);
        block = block == null ? Optional.empty() : block;
    }

    /** The position of {@code defects} at {@code asAt}, against a period ending on {@code periodEnds}. */
    public static DefectsReport build(List<Defect> defects, LocalDate periodEnds, LocalDate asAt) {
        LocalDate extended = Defect.periodEndsAt(defects, periodEnds, asAt);
        return new DefectsReport(
                asAt,
                extended,
                SiteDates.diffDays(periodEnds, extended),
                Defect.summarise(defects, asAt),
                Defect.outstanding(defects, asAt),
                Defect.overdue(defects, asAt),
                Defect.certificateBlock(defects, periodEnds, asAt));
    }

    /** The report as plain text: the counts, then the open defects when there are any. */
    public String render() {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("defects at " + SiteDates.format(asAt)));
        sections.add("");
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        pairs.add(Map.entry("period ends", SiteDates.format(periodEnds)));
        // Only shown when a defect kept the works out of use, so an ordinary list reads as before.
        if (extendedBy > 0) {
            pairs.add(Map.entry("extended by", extendedBy + " days out of use"));
        }
        pairs.add(Map.entry("outstanding", String.valueOf(summary.outstanding())));
        pairs.add(Map.entry("overdue", String.valueOf(summary.overdue())));
        pairs.add(Map.entry("safety", String.valueOf(summary.bySeverity().get(DefectSeverity.SAFETY))));
        pairs.add(Map.entry("major", String.valueOf(summary.bySeverity().get(DefectSeverity.MAJOR))));
        pairs.add(Map.entry("minor", String.valueOf(summary.bySeverity().get(DefectSeverity.MINOR))));
        pairs.add(Map.entry("cause not agreed", String.valueOf(summary.disputed())));
        pairs.add(Map.entry(
                "certificate", block.map(b -> b.wire().replace('_', ' ')).orElse("can be issued")));
        sections.add(Table.pairs(pairs));

        if (!outstanding.isEmpty()) {
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("defect"),
                            Column.of("package"),
                            Column.of("where"),
                            Column.of("severity"),
                            Column.of("due"),
                            Column.of("cause"),
                            Column.of("description").withWidth(40)),
                    outstanding.stream()
                            .map(defect -> List.of(
                                    defect.reference(),
                                    defect.packageId(),
                                    defect.extent().map(ChainageRange::format).orElse(""),
                                    defect.severity().wire(),
                                    SiteDates.format(defect.dueBy()),
                                    defect.attribution().wire(),
                                    defect.description()))
                            .toList()));
        }
        return String.join("\n", sections);
    }

    /** The chase list: overdue first, then everything else by due date. */
    public List<String> chaseList() {
        return Stream.concat(overdue.stream(), outstanding.stream().filter(d -> !overdue.contains(d)))
                .map(Defect::describe)
                .toList();
    }
}
