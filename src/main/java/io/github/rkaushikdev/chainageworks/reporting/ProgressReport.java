package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.itp.GateState;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister.ReconciliationLine;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet.OverMeasure;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.progress.StepProgressDetail;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The monthly progress report.
 *
 * <p>Written around one number that a valuation on its own never shows: the value of work that is
 * in the ground and cannot be paid for. Somebody has to chase those sign-offs, and until the report
 * puts a figure on them nobody does.
 *
 * @param period the valuation period reported on
 * @param billRevision the reference of the bill revision in force at the cut-off
 * @param lines one line per package, in register order
 * @param budget the packages' budgets added up
 * @param earned what the packages have earned, added up
 * @param heldValue the value held back across the job
 * @param workingDays working days in the period
 * @param productiveDays working days in the period that nobody stood down
 * @param standdowns the standdowns in the period
 * @param reconciliation where the packages and the bill disagree
 * @param overMeasures survey measurements past their allowance
 */
public record ProgressReport(
        ValuationPeriod period,
        String billRevision,
        List<PackageLine> lines,
        Money budget,
        Money earned,
        Money heldValue,
        int workingDays,
        int productiveDays,
        List<Standdown> standdowns,
        List<ReconciliationLine> reconciliation,
        List<OverMeasure> overMeasures) {

    public ProgressReport {
        lines = List.copyOf(lines);
        standdowns = List.copyOf(standdowns);
        reconciliation = List.copyOf(reconciliation);
        overMeasures = List.copyOf(overMeasures);
    }

    /**
     * One step of a package as the report shows it.
     *
     * @param stepId the step
     * @param builtFraction built length over package length, whatever the gates say
     * @param creditedFraction credited length over package length
     * @param gate whether the gates are open, part open or held
     * @param heldPoints the hold points releasing nothing
     * @param heldBack what has been built and cannot be credited yet
     */
    public record StepLine(
            String stepId,
            double builtFraction,
            double creditedFraction,
            GateState gate,
            List<String> heldPoints,
            List<ChainageRange> heldBack) {

        public StepLine {
            heldPoints = List.copyOf(heldPoints);
            heldBack = List.copyOf(heldBack);
        }
    }

    /**
     * One package as the report shows it.
     *
     * @param packageId the package
     * @param name its name
     * @param section the section of the works it sits in
     * @param extent the stretch it covers
     * @param budget what it is worth at the bill rates in force
     * @param earned what it has earned
     * @param fraction the earned fraction
     * @param earnedIfReleased what the same work would have earned with every gate open
     * @param heldValue the difference: value sitting behind a hold point or an open report
     * @param steps the steps of its rule of credit
     */
    public record PackageLine(
            String packageId,
            String name,
            String section,
            ChainageRange extent,
            Money budget,
            Money earned,
            Percent fraction,
            Money earnedIfReleased,
            Money heldValue,
            List<StepLine> steps) {

        public PackageLine {
            steps = List.copyOf(steps);
        }
    }

    /** The report for a period, read at its cut-off. */
    public static ProgressReport build(ProgressContext context, ValuationPeriod period) {
        LocalDate asAt = period.cutOff();
        Bill bill = context.billHistory().billAsAt(asAt);
        List<PackageLine> lines = context.register().packages().stream()
                .map(pkg -> toLine(context, pkg, PackageEarning.earn(context, pkg, asAt)))
                .toList();

        return new ProgressReport(
                period,
                context.billHistory().asAt(asAt).reference(),
                lines,
                Money.sum(lines.stream().map(PackageLine::budget).toList(), bill.currency()),
                Money.sum(lines.stream().map(PackageLine::earned).toList(), bill.currency()),
                Money.sum(lines.stream().map(PackageLine::heldValue).toList(), bill.currency()),
                context.calendar().workingDaysIn(period.range()),
                context.calendar().productiveDaysIn(period.range()),
                context.calendar().standdownsIn(period.range()),
                context.register().reconcileWith(bill),
                context.tolerances().findOverMeasures(context.records(), context.register(), asAt));
    }

    private static PackageLine toLine(ProgressContext context, WorkPackage pkg, PackageEarning earning) {
        Percent released = context.profileFor(pkg).earnedFraction(StepProgressDetail.toBuiltProgress(earning.steps()));
        Money earnedIfReleased = released.of(earning.budget());
        return new PackageLine(
                earning.packageId(),
                pkg.name(),
                pkg.section(),
                pkg.extent(),
                earning.budget(),
                earning.earnedValue(),
                earning.fraction(),
                earnedIfReleased,
                earnedIfReleased.minus(earning.earnedValue()),
                earning.steps().stream().map(ProgressReport::toStepLine).toList());
    }

    private static StepLine toStepLine(StepProgressDetail step) {
        return new StepLine(
                step.stepId(),
                step.builtFraction(),
                step.fraction(),
                step.gate(),
                step.heldPoints(),
                step.heldBackExtent());
    }

    /** Packages with value stuck behind a gate, worst first. */
    public List<PackageLine> heldLines() {
        return lines.stream()
                .filter(l -> l.heldValue().minor() > 0)
                .sorted(Comparator.comparingLong(
                                (PackageLine l) -> l.heldValue().minor())
                        .reversed())
                .toList();
    }

    /** Every hold point named anywhere in the report, once each, in order. */
    public List<String> outstandingHoldPoints() {
        Set<String> out = new TreeSet<>();
        for (PackageLine line : lines) {
            for (StepLine step : line.steps()) {
                out.addAll(step.heldPoints());
            }
        }
        return List.copyOf(out);
    }

    /** True when the packages disagree with the bill or the survey has run past its allowance. */
    public boolean hasProblems() {
        return !reconciliation.isEmpty() || !overMeasures.isEmpty();
    }
}
