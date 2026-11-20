package io.github.rkaushikdev.chainageworks.domain.survey;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * How much more than the allocation each class of work may measure before it is reported.
 *
 * <p>Earthworks routinely overruns because the ground is not what the borehole said, and a count
 * of manhole covers does not. One allowance for the whole job therefore either buries the
 * earthworks or floods the report with drainage noise, so the allowance is set per work class.
 *
 * @param rules one rule per work class, plus the fallback rule everything else uses
 */
public record ToleranceSet(List<Rule> rules) {

    public ToleranceSet {
        rules = List.copyOf(rules);
    }

    /**
     * The allowance for one work class.
     *
     * @param workClass the class letter, or absent for the fallback rule
     * @param allowance how far over the allocation it may measure
     */
    public record Rule(Optional<String> workClass, Percent allowance) {

        public Rule {
            workClass = workClass == null ? Optional.empty() : workClass;
        }

        /** The rule for one work class. */
        public static Rule forClass(String workClass, Percent allowance) {
            return new Rule(Optional.of(workClass), allowance);
        }

        /** The rule every class without one of its own falls back to. */
        public static Rule fallback(Percent allowance) {
            return new Rule(Optional.empty(), allowance);
        }
    }

    /**
     * A measurement that has run past its allowance.
     *
     * @param packageId the package
     * @param code the bill item
     * @param allocated what the package was allocated
     * @param measured what has been measured
     * @param allowed the allocation plus the allowance
     * @param excess how far past the allowance it is
     * @param message the line as a person reads it
     */
    public record OverMeasure(
            String packageId,
            ItemCode code,
            Quantity allocated,
            Quantity measured,
            Quantity allowed,
            Quantity excess,
            String message) {}

    /** The allowances a job starts with. */
    public static final ToleranceSet DEFAULT = new ToleranceSet(List.of(
            Rule.fallback(Percent.of(2.5)),
            Rule.forClass("E", Percent.of(10)),
            Rule.forClass("D", Percent.of(15)),
            Rule.forClass("K", Percent.of(0)),
            Rule.forClass("I", Percent.of(5))));

    /** Build a set, refusing two rules for one class, a negative allowance and a missing fallback. */
    public static ToleranceSet build(List<Rule> rules) {
        Set<String> seen = new HashSet<>();
        for (Rule rule : rules) {
            String key = rule.workClass().orElse("*");
            if (!seen.add(key)) {
                throw new BadInputException(
                        "two tolerance rules cover the same work class", Details.of("workClass", key));
            }
            if (rule.allowance().basisPoints() < 0) {
                throw new BadInputException("a tolerance allowance cannot be negative", Details.of("workClass", key));
            }
        }
        if (!seen.contains("*")) {
            throw new BadInputException("a tolerance set needs a fallback rule");
        }
        return new ToleranceSet(rules);
    }

    /** The allowance a code's work class gets. */
    public Percent allowanceFor(ItemCode code) {
        Optional<Rule> specific = rules.stream()
                .filter(r -> r.workClass().equals(Optional.of(code.workClass())))
                .findFirst();
        if (specific.isPresent()) {
            return specific.get().allowance();
        }
        return rules.stream()
                .filter(r -> r.workClass().isEmpty())
                .findFirst()
                .orElseThrow(() -> new BadInputException("a tolerance set needs a fallback rule"))
                .allowance();
    }

    /** What the package may measure before anybody has to explain themselves. */
    public Optional<Quantity> allowedQuantity(WorkPackage pkg, ItemCode code) {
        return pkg.findAllocation(code).map(allocation -> {
            Percent allowance = allowanceFor(code);
            return allocation.quantity().plus(allowance.of(allocation.quantity()));
        });
    }

    /**
     * Measurements that have run past what the package was allocated, plus the allowance for that
     * class of work.
     *
     * <p>This is not a hard stop. Overrun is normal on a measured contract and the point of the
     * report is that somebody prices it before the certificate goes out, not that the survey is
     * refused.
     */
    public List<OverMeasure> findOverMeasures(List<SurveyRecord> records, PackageRegister register, LocalDate asAt) {
        List<OverMeasure> out = new ArrayList<>();
        for (WorkPackage pkg : register.packages()) {
            for (PackageAllocation allocation : pkg.allocations()) {
                Quantity measured = SurveyRecord.measuredQuantity(
                                records,
                                asAt,
                                RecordQuery.ALL.withPackage(pkg.id()).withCode(allocation.code()))
                        .orElse(Quantity.zero(allocation.quantity().unit()));
                Optional<Quantity> allowed = allowedQuantity(pkg, allocation.code());
                if (allowed.isEmpty() || measured.milli() <= allowed.get().milli()) {
                    continue;
                }
                Quantity excess = measured.minus(allowed.get());
                out.add(new OverMeasure(
                        pkg.id(),
                        allocation.code(),
                        allocation.quantity(),
                        measured,
                        allowed.get(),
                        excess,
                        pkg.id() + " " + allocation.code().format() + " measured " + measured.format()
                                + " against an allowance of " + allowed.get().format()
                                + " (" + allocation.quantity().format() + " plus "
                                + allowanceFor(allocation.code()).format() + ")"));
            }
        }
        return List.copyOf(out);
    }

    /** Records booked outside the stretch their package covers. */
    public static List<SurveyRecord> findRecordsOutsideExtent(List<SurveyRecord> records, PackageRegister register) {
        return records.stream()
                .filter(record -> register.find(record.packageId())
                        .map(pkg -> record.extent().from() < pkg.extent().from()
                                || record.extent().to() > pkg.extent().to())
                        .orElse(false))
                .toList();
    }

    /** Records whose package is not in the register at all. */
    public static List<SurveyRecord> findOrphanRecords(List<SurveyRecord> records, PackageRegister register) {
        Set<String> ids = new HashSet<>();
        register.packages().forEach(p -> ids.add(p.id()));
        return records.stream().filter(r -> !ids.contains(r.packageId())).toList();
    }
}
