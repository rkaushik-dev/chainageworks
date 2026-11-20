package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.survey.RecordQuery;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What a package has earned at a cut-off date, and how.
 *
 * @param packageId the package
 * @param fraction the earned fraction the rule of credit worked out
 * @param steps the step detail behind it
 * @param allocations what each allocation earned
 * @param budget what the package is worth at the bill rates in force
 * @param earnedValue what it has earned
 */
public record PackageEarning(
        String packageId,
        Percent fraction,
        List<StepProgressDetail> steps,
        List<Allocation> allocations,
        Money budget,
        Money earnedValue) {

    public PackageEarning {
        steps = List.copyOf(steps);
        allocations = List.copyOf(allocations);
    }

    /**
     * What one allocation earned.
     *
     * @param code the bill item
     * @param allocated what the package carries of it
     * @param measured what the survey book has measured against it
     * @param earnedQuantity what it has earned
     * @param rate the rate in force
     * @param earnedValue what that is worth
     * @param cappedByMeasurement true when the survey book, not the rule of credit, decided the
     *     figure
     * @param notEarnable true when the item is provisional and earns nothing whatever was built
     */
    public record Allocation(
            ItemCode code,
            Quantity allocated,
            Quantity measured,
            Quantity earnedQuantity,
            Rate rate,
            Money earnedValue,
            boolean cappedByMeasurement,
            boolean notEarnable) {}

    /**
     * What a package has earned at a cut-off date.
     *
     * <p>Five separate rules land on the same number and they are applied in this order, which is
     * the order the contract applies them in:
     *
     * <ol>
     *   <li>the survey book says what was built,
     *   <li>the hold points and the open reports say how much of that may count,
     *   <li>the rule of credit turns the step fractions into one earned fraction,
     *   <li>the bill revision in force at the cut-off prices it,
     *   <li>the measurement caps it, because nothing may be earned that was not built.
     * </ol>
     *
     * <p>Step five is the one that keeps being left out of a spreadsheet version, and it is the one
     * that stops a generous rule of credit paying for work that only exists on the programme.
     */
    public static PackageEarning earn(ProgressContext context, WorkPackage pkg, LocalDate asAt) {
        Bill bill = context.billHistory().billAsAt(asAt);
        CreditProfile profile = context.profileFor(pkg);
        List<StepProgressDetail> steps = StepProgressDetail.forPackage(context, pkg, asAt);
        Percent fraction = profile.earnedFraction(StepProgressDetail.toStepProgress(steps));
        List<Allocation> allocations = pkg.allocations().stream()
                .map(allocation -> earnAllocation(context, pkg, bill, allocation, fraction, asAt))
                .toList();
        return new PackageEarning(
                pkg.id(),
                fraction,
                steps,
                allocations,
                Money.sum(
                        allocations.stream()
                                .map(a -> a.rate().extend(a.allocated()))
                                .toList(),
                        bill.currency()),
                Money.sum(allocations.stream().map(Allocation::earnedValue).toList(), bill.currency()));
    }

    private static Allocation earnAllocation(
            ProgressContext context,
            WorkPackage pkg,
            Bill bill,
            PackageAllocation allocation,
            Percent fraction,
            LocalDate asAt) {
        BillItem item = bill.find(allocation.code())
                .orElseThrow(() ->
                        new NotFoundException("bill item", allocation.code().format()));
        Quantity allocated = allocation.quantity();
        Quantity measured = SurveyRecord.measuredQuantity(
                        context.records(),
                        asAt,
                        RecordQuery.ALL.withPackage(pkg.id()).withCode(item.code()))
                .orElse(Quantity.zero(allocated.unit()));
        if (!item.isEarnable()) {
            return new Allocation(
                    item.code(),
                    allocated,
                    measured,
                    Quantity.zero(allocated.unit()),
                    item.rate(),
                    Money.zero(item.rate().currency()),
                    false,
                    true);
        }
        // A lump sum has nothing to measure, so the rule of credit is the only thing that can decide
        // it: it is neither rounded to the bill precision, which would round a part earned sum to
        // nothing, nor capped by a survey book that will never carry a record for it.
        Quantity scaled = fraction.of(allocated);
        boolean measurable = allocated.unit().isMeasurable();
        Quantity target = measurable ? scaled.roundedToBillPrecision() : scaled;
        Quantity capped = measurable ? Quantity.min(target, measured) : target;
        return new Allocation(
                item.code(),
                allocated,
                measured,
                capped,
                item.rate(),
                item.rate().extend(capped),
                capped.milli() < target.milli(),
                false);
    }

    /** What every package in the register has earned. */
    public static List<PackageEarning> earnAll(ProgressContext context, LocalDate asAt) {
        return context.register().packages().stream()
                .map(pkg -> earn(context, pkg, asAt))
                .toList();
    }

    /** The earnings added up, in the currency of the bill in force. */
    public static Money totalEarned(List<PackageEarning> earnings, ProgressContext context, LocalDate asAt) {
        return Money.sum(
                earnings.stream().map(PackageEarning::earnedValue).toList(),
                context.billHistory().billAsAt(asAt).currency());
    }

    public static Optional<PackageEarning> find(List<PackageEarning> earnings, String packageId) {
        return earnings.stream().filter(e -> e.packageId().equals(packageId)).findFirst();
    }

    /** The allocations the survey book held down. */
    public List<Allocation> cappedAllocations() {
        return allocations.stream().filter(Allocation::cappedByMeasurement).toList();
    }
}
