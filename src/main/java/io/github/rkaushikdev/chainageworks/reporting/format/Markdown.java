package io.github.rkaushikdev.chainageworks.reporting.format;

import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport;
import io.github.rkaushikdev.chainageworks.util.Text;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The certificate, the progress report and the subcontract payments as Markdown documents, and the
 * table, heading and list pieces they are built from.
 */
public final class Markdown {

    private static final Pattern LINE_BREAK = Pattern.compile("\r?\n");
    private static final String AMOUNT_DUE = "amount due this certificate";

    private Markdown() {}

    /**
     * Escape the characters that would otherwise be markup.
     *
     * <p>A pipe is the one that matters: a bill description reading {@code 300 mm | 450 mm} silently
     * splits a table row into the wrong number of cells, and the resulting document renders with the
     * columns shifted from that row down. A line break would end the row, so it becomes a space.
     */
    public static String escapeCell(String text) {
        return LINE_BREAK.matcher(text.replace("|", "\\|")).replaceAll(" ");
    }

    /** A pipe table: the headings, the alignment rule and one line per row, every cell escaped. */
    public static String table(List<Column> columns, List<List<String>> rows) {
        String heading =
                "| " + columns.stream().map(c -> escapeCell(c.heading())).collect(Collectors.joining(" | ")) + " |";
        String rule = "| "
                + columns.stream()
                        .map(c -> c.align() == Align.RIGHT ? "---:" : ":---")
                        .collect(Collectors.joining(" | "))
                + " |";
        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add(heading);
        lines.add(rule);
        for (List<String> row : rows) {
            lines.add("| " + row.stream().map(Markdown::escapeCell).collect(Collectors.joining(" | ")) + " |");
        }
        return String.join("\n", lines);
    }

    /** A second level heading. */
    public static String heading(String text) {
        return heading(text, 2);
    }

    /** A heading at {@code level}, one being the document title. */
    public static String heading(String text, int level) {
        return "#".repeat(level) + " " + text;
    }

    /** Label and value pairs as a list with the labels in bold. */
    public static String pairs(List<Map.Entry<String, String>> pairs) {
        return pairs.stream()
                .map(pair -> "- **" + escapeCell(pair.getKey()) + "**: " + escapeCell(pair.getValue()))
                .collect(Collectors.joining("\n"));
    }

    /** Lift the first letter, because a Markdown table reads as prose. */
    private static String sentenceCase(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Text.upper(text.substring(0, 1)) + text.substring(1);
    }

    /**
     * A certificate as a Markdown document.
     *
     * <p>The same figures as the plain text version, in the same order, because the two are printed
     * from one job and somebody always compares them. The only differences are the ones Markdown
     * forces, and the amount due is set in bold.
     */
    public static String certificate(Certificate certificate) {
        ValuationPeriod period = certificate.period();
        List<String> sections = new ArrayList<>();
        sections.add(heading("Payment certificate " + period.number(), 1));
        sections.add("");
        sections.add(pairs(List.of(
                Map.entry("Valuation to", SiteDates.format(period.cutOff())),
                Map.entry("Issued", SiteDates.format(period.issuedOn())),
                Map.entry("Payment due", SiteDates.format(period.dueOn())),
                Map.entry("Bill revision", certificate.billRevision()))));
        sections.add("");
        sections.add(table(
                List.of(Column.of("Item"), Column.right("Amount")),
                certificate.lines().stream()
                        .map(line -> {
                            String name = sentenceCase(line.name());
                            return AMOUNT_DUE.equals(line.name())
                                    ? List.of(
                                            "**" + name + "**",
                                            "**" + line.amount().format() + "**")
                                    : List.of(name, line.amount().format());
                        })
                        .toList()));

        for (String note : certificate.notes()) {
            sections.add("");
            sections.add("> " + sentenceCase(note) + ".");
        }
        return String.join("\n", sections);
    }

    /** The progress report as a Markdown document: the headline figures, then the package table. */
    public static String progress(ProgressReport report) {
        return String.join(
                "\n",
                heading("Progress at " + SiteDates.format(report.period().cutOff()), 1),
                "",
                pairs(List.of(
                        Map.entry("Valuation", String.valueOf(report.period().number())),
                        Map.entry("Bill revision", report.billRevision()),
                        Map.entry("Budget", report.budget().format()),
                        Map.entry("Earned", report.earned().format()),
                        Map.entry(
                                "Held by gates and reports", report.heldValue().format()),
                        Map.entry("Working days in the period", String.valueOf(report.workingDays())),
                        Map.entry(
                                "Days stood down",
                                String.valueOf(report.standdowns().size())))),
                "",
                heading("Packages"),
                "",
                table(
                        List.of(
                                Column.of("Package"),
                                Column.of("Section"),
                                Column.right("Budget"),
                                Column.right("Earned"),
                                Column.right("Held")),
                        report.lines().stream()
                                .map(line -> List.of(
                                        line.packageId(),
                                        line.section(),
                                        line.budget().format(),
                                        line.earned().format(),
                                        line.heldValue().format()))
                                .toList()));
    }

    /**
     * The subcontract payment position as a Markdown document. The order table and the notice diary
     * are left out when they would be empty.
     */
    public static String subcontract(SubcontractReport report) {
        List<String> sections = new ArrayList<>();
        sections.add(heading("Subcontract payments at " + SiteDates.format(report.asAt()), 1));
        sections.add("");
        sections.add(pairs(List.of(
                Map.entry("Orders", String.valueOf(report.positions().size())),
                Map.entry(
                        "Notices missed",
                        String.valueOf(report.diary().stream()
                                .filter(SubcontractReport.NoticeDiaryEntry::missed)
                                .count())),
                Map.entry("Exposed by default", report.exposure()),
                Map.entry(
                        "Orders with barred set off",
                        String.valueOf(report.barredContra().size())))));

        if (!report.positions().isEmpty()) {
            sections.add("");
            sections.add(table(
                    List.of(
                            Column.of("Order"),
                            Column.of("Contractor"),
                            Column.right("Certified"),
                            Column.right("Set off"),
                            Column.right("Net")),
                    report.positions().stream()
                            .map(position -> List.of(
                                    position.subcontract().id(),
                                    position.subcontract().contractor(),
                                    position.certifiedToDate().format(),
                                    position.contra().deductible().format(),
                                    position.netOfContra().format()))
                            .toList()));
        }

        if (!report.diary().isEmpty()) {
            sections.add("");
            sections.add(heading("Notice diary"));
            sections.add("");
            sections.add(table(
                    List.of(
                            Column.of("Application"),
                            Column.of("Contractor"),
                            Column.of("Notice by"),
                            Column.right("Applied for"),
                            Column.of("State")),
                    report.diary().stream()
                            .map(entry -> List.of(
                                    entry.applicationId(),
                                    entry.contractor(),
                                    SiteDates.format(entry.noticeBy()),
                                    entry.amount(),
                                    entry.missed() ? "window closed" : "open"))
                            .toList()));
        }
        return String.join("\n", sections);
    }
}
