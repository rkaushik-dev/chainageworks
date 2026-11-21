package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.progress.PackageOutput;
import io.github.rkaushikdev.chainageworks.progress.PackageOutput.StepOutput;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * What the job produced for the hours it booked, package by package.
 *
 * <p>Built on the same cut-off as the valuation so the two can be read side by side. A productivity
 * figure taken to a different date than the certificate invites the argument that the hours were
 * spent on work that has not been measured yet, and that argument is unanswerable when the dates
 * differ.
 *
 * @param asAt the cut-off
 * @param packages the output of each package
 * @param totals the returns up to the cut-off, added up
 * @param lostFraction the share of all booked hours that bought nothing
 * @param behind steps running behind their allowance, worst first, across every package
 * @param barren steps with hours booked and nothing built to show for them
 */
public record OutputReport(
        LocalDate asAt,
        List<PackageOutput> packages,
        ResourceReturn.Totals totals,
        double lostFraction,
        List<PackageStep> behind,
        List<PackageStep> barren) {

    public OutputReport {
        packages = List.copyOf(packages);
        behind = List.copyOf(behind);
        barren = List.copyOf(barren);
    }

    /**
     * A step's output, with the package it belongs to.
     *
     * @param packageId the package
     * @param step the step's output
     */
    public record PackageStep(String packageId, StepOutput step) {}

    /**
     * Hours a package booked without naming one of its steps.
     *
     * @param packageId the package
     * @param hours the hours
     */
    public record UnallocatedHours(String packageId, double hours) {}

    /** Set what each package built against the hours booked to it, up to {@code asAt}. */
    public static OutputReport build(
            ProgressContext context,
            List<WorkPackage> packages,
            List<ResourceReturn> returns,
            List<OutputAllowance> allowances,
            LocalDate asAt) {
        List<PackageOutput> outputs = packages.stream()
                .map(pkg -> PackageOutput.of(context, pkg, returns, asAt, allowances))
                .toList();
        ResourceReturn.Totals totals = ResourceReturn.total(ResourceReturn.returnsTo(returns, asAt));

        List<PackageStep> behind = outputs.stream()
                .flatMap(output ->
                        output.behindAllowance().stream().map(step -> new PackageStep(output.packageId(), step)))
                .sorted(Comparator.comparingDouble(entry -> entry.step().ratio().orElse(0)))
                .toList();

        List<PackageStep> barren = outputs.stream()
                .flatMap(output ->
                        output.nothingToShow().stream().map(step -> new PackageStep(output.packageId(), step)))
                .toList();

        return new OutputReport(asAt, outputs, totals, totals.lostFraction(), behind, barren);
    }

    private static String rate(OptionalDouble value) {
        return value.isPresent() ? Numbers.toFixed(value.getAsDouble(), 2) : "";
    }

    /** The report as plain text: the totals, the step table and the hours with nothing to show. */
    public String render() {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("output at " + SiteDates.format(asAt)));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("returns", String.valueOf(totals.returns())),
                Map.entry("days worked", String.valueOf(totals.days())),
                Map.entry("labour hours", Numbers.format(totals.labourHours())),
                Map.entry("plant hours", Numbers.format(totals.plantHours())),
                Map.entry("standing hours", Numbers.format(totals.standingHours())),
                Map.entry("idle hours", Numbers.format(totals.idleHours())),
                Map.entry("hours that bought nothing", Numbers.toFixed(lostFraction * 100, 1) + " percent"),
                Map.entry("steps behind the allowance", String.valueOf(behind.size())))));

        List<List<String>> rows = packages.stream()
                .flatMap(output -> output.steps().stream()
                        .filter(step -> step.labourHours() > 0 || step.builtMetres() > 0)
                        .map(step -> List.of(
                                output.packageId(),
                                step.stepId(),
                                Numbers.toFixed(step.builtMetres(), 0),
                                Numbers.format(step.labourHours()),
                                Numbers.format(step.plantHours()),
                                rate(step.achieved()),
                                rate(step.allowed()),
                                step.ratio().isPresent()
                                        ? Numbers.toFixed(step.ratio().getAsDouble() * 100, 0) + "%"
                                        : "")))
                .toList();

        if (!rows.isEmpty()) {
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("package"),
                            Column.of("step"),
                            Column.right("metres"),
                            Column.right("labour hr"),
                            Column.right("plant hr"),
                            Column.right("m per hr"),
                            Column.right("allowed"),
                            Column.right("against")),
                    rows));
        }

        if (!barren.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("hours with nothing measured against them", "-"));
            for (PackageStep entry : barren) {
                sections.add("  " + entry.packageId() + " " + entry.step().stepId() + ", "
                        + Numbers.format(entry.step().labourHours()) + " labour hours");
            }
        }
        return String.join("\n", sections);
    }

    /** The hours each package booked without naming a step, worst first. */
    public List<UnallocatedHours> unallocatedHours() {
        return packages.stream()
                .filter(output -> output.unallocatedHours() > 0)
                .map(output -> new UnallocatedHours(output.packageId(), output.unallocatedHours()))
                .sorted(Comparator.comparingDouble(UnallocatedHours::hours).reversed())
                .toList();
    }
}
