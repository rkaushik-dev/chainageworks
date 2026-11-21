package io.github.rkaushikdev.chainageworks.reporting.format;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister.ReconciliationLine;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet.OverMeasure;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.reporting.CurvePoint;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport.PackageLine;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.valuation.CashFlow;
import io.github.rkaushikdev.chainageworks.valuation.CashPoint;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The progress report, the certificate, the S-curve, the reconciliation and the cash forecast as
 * plain text for a terminal or a printed page.
 */
public final class PlainText {

    private PlainText() {}

    /** The package table that sits at the top of a progress report. */
    public static String packageTable(List<PackageLine> lines) {
        return Table.render(
                List.of(
                        Column.of("package"),
                        Column.of("name").withWidth(34),
                        Column.right("budget"),
                        Column.right("earned"),
                        Column.right("percent"),
                        Column.right("held")),
                lines.stream()
                        .map(line -> List.of(
                                line.packageId(),
                                line.name(),
                                line.budget().format(),
                                line.earned().format(),
                                line.fraction().format(1),
                                line.heldValue().format()))
                        .toList());
    }

    /** One package broken down step by step, for the backing sheet. */
    public static String stepTable(PackageLine line) {
        return Table.render(
                List.of(
                        Column.of("step"),
                        Column.right("built"),
                        Column.right("credited"),
                        Column.of("gate"),
                        Column.of("held back")),
                line.steps().stream()
                        .map(step -> List.of(
                                step.stepId(),
                                Numbers.toFixed(step.builtFraction() * 100, 1) + "%",
                                Numbers.toFixed(step.creditedFraction() * 100, 1) + "%",
                                spaced(step.gate().wire()),
                                step.heldBack().stream()
                                        .map(ChainageRange::format)
                                        .collect(Collectors.joining(", "))))
                        .toList());
    }

    /**
     * The whole progress report as plain text.
     *
     * <p>Ordered the way it gets read: the money first, then what is stopping the money, then the
     * housekeeping. A reader who stops after the first two sections has everything they need to act.
     * A section with nothing in it is left out.
     */
    public static String progressReport(ProgressReport report) {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("progress report, " + report.period().describe()));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("bill revision", report.billRevision()),
                Map.entry("budget", report.budget().format()),
                Map.entry("earned", report.earned().format()),
                Map.entry("held back", report.heldValue().format()),
                Map.entry("working days", String.valueOf(report.workingDays())),
                Map.entry("productive days", String.valueOf(report.productiveDays())))));
        sections.add("");
        sections.add(packageTable(report.lines()));

        List<PackageLine> held = report.heldLines();
        if (!held.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("work built and not payable", "-"));
            sections.add("");
            for (PackageLine line : held) {
                sections.add(line.packageId() + " " + line.heldValue().format());
                sections.add(Table.indent(stepTable(line)));
                sections.add("");
            }
            List<String> points = report.outstandingHoldPoints();
            if (!points.isEmpty()) {
                sections.add("hold points outstanding: " + String.join(", ", points));
            }
        }

        if (!report.standdowns().isEmpty()) {
            sections.add("");
            sections.add(Table.heading("days lost", "-"));
            sections.add("");
            for (Standdown standdown : report.standdowns()) {
                String note = standdown
                        .note()
                        .filter(text -> !text.isEmpty())
                        .map(text -> "  " + text)
                        .orElse("");
                sections.add(SiteDates.format(standdown.date()) + "  "
                        + spaced(standdown.reason().wire()) + note);
            }
        }

        if (report.hasProblems()) {
            sections.add("");
            sections.add(Table.heading("to look at", "-"));
            sections.add("");
            for (ReconciliationLine line : report.reconciliation()) {
                sections.add(line.message());
            }
            for (OverMeasure over : report.overMeasures()) {
                sections.add(over.message());
            }
        }

        return String.join("\n", sections);
    }

    /** The certificate as the one page a payer actually signs. */
    public static String certificate(Certificate certificate) {
        ValuationPeriod period = certificate.period();
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("payment certificate " + period.number()));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("valuation to", SiteDates.format(period.cutOff())),
                Map.entry("issued", SiteDates.format(period.issuedOn())),
                Map.entry("payment due", SiteDates.format(period.dueOn())),
                Map.entry("bill revision", certificate.billRevision()))));
        sections.add("");
        sections.add(Table.render(
                List.of(Column.of("item"), Column.right("amount")),
                certificate.lines().stream()
                        .map(line -> List.of(line.name(), line.amount().format()))
                        .toList()));
        List<String> notes = certificate.notes();
        if (!notes.isEmpty()) {
            sections.add("");
            sections.addAll(notes);
        }
        return String.join("\n", sections);
    }

    /** The S-curve as a table, one row per period. */
    public static String curve(List<CurvePoint> points) {
        return Table.render(
                List.of(
                        Column.right("period"),
                        Column.of("cut off"),
                        Column.right("planned"),
                        Column.right("earned"),
                        Column.right("variance"),
                        Column.right("earned %")),
                points.stream()
                        .map(point -> List.of(
                                String.valueOf(point.period()),
                                SiteDates.format(point.cutOff()),
                                point.planned().format(),
                                point.earned().format(),
                                point.variance().format(),
                                point.earnedShare().format(1)))
                        .toList());
    }

    /** The reconciliation, as the list a surveyor works through, or a line saying there is nothing. */
    public static String reconciliation(ProgressReport report) {
        if (report.reconciliation().isEmpty()) {
            return "the packages reconcile against the bill";
        }
        return Table.render(
                List.of(Column.of("code"), Column.of("problem"), Column.of("billed"), Column.of("allocated")),
                report.reconciliation().stream()
                        .map(line -> List.of(
                                line.code().format(),
                                spaced(line.problem().wire()),
                                quantity(line.billed()),
                                quantity(line.allocated())))
                        .toList());
    }

    /**
     * The cash forecast as a table, with the worst month called out under it.
     *
     * <p>The peak is printed separately because it is the only line anybody acts on. A finance
     * director reads this table to answer one question, which is how big the overdraft has to be,
     * and that answer is one number in the middle of the table rather than the last row.
     */
    public static String cashFlow(List<CashPoint> points) {
        String table = Table.render(
                List.of(
                        Column.right("period"),
                        Column.of("cut off"),
                        Column.of("due"),
                        Column.right("certificate"),
                        Column.right("receipt"),
                        Column.right("outstanding")),
                points.stream()
                        .map(point -> List.of(
                                String.valueOf(point.period()),
                                SiteDates.format(point.cutOff()),
                                SiteDates.format(point.dueOn()),
                                point.certificate().format(),
                                point.receipt().format(),
                                point.outstanding().format()))
                        .toList());
        return CashFlow.peakExposure(points)
                .map(peak ->
                        table + "\n\nmost owed at once " + peak.outstanding().format() + " in period " + peak.period()
                                + ", due " + SiteDates.format(peak.dueOn()))
                .orElse(table);
    }

    private static String spaced(String wire) {
        return wire.replace('_', ' ');
    }

    private static String quantity(Optional<Quantity> value) {
        return value.map(Quantity::format).orElse("");
    }
}
