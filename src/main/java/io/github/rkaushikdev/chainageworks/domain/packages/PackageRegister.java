package io.github.rkaushikdev.chainageworks.domain.packages;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every work package on the job, in order along the alignment.
 *
 * @param packages the packages, sorted by where they start and then by id
 */
public record PackageRegister(List<WorkPackage> packages) {

    public PackageRegister {
        packages = List.copyOf(packages);
    }

    /** What the reconciliation found wrong with one bill item. */
    public enum Problem {
        UNKNOWN_ITEM("unknown_item"),
        OVER_ALLOCATED("over_allocated"),
        UNDER_ALLOCATED("under_allocated"),
        NOT_ALLOCATED("not_allocated");

        private final String wire;

        Problem(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        @Override
        public String toString() {
            return wire;
        }
    }

    /**
     * One bill item the packages and the bill disagree about.
     *
     * @param code the bill item
     * @param problem what is wrong
     * @param billed what the bill carries, absent when the bill has no such item
     * @param allocated what the packages carry, absent when nothing is allocated
     * @param message the line as a person reads it
     */
    public record ReconciliationLine(
            ItemCode code, Problem problem, Optional<Quantity> billed, Optional<Quantity> allocated, String message) {}

    /** A pair of packages that overlap and share a bill item. */
    public record Clash(WorkPackage first, WorkPackage second) {}

    /** Build a register, refusing two packages that share an id. */
    public static PackageRegister build(List<WorkPackage> packages) {
        Set<String> seen = new HashSet<>();
        for (WorkPackage pkg : packages) {
            if (!seen.add(pkg.id())) {
                throw new BadInputException("two work packages share an id", Details.of("package", pkg.id()));
            }
        }
        List<WorkPackage> sorted = new ArrayList<>(packages);
        sorted.sort((a, b) -> {
            int byStart = Long.compare(a.extent().from(), b.extent().from());
            if (byStart != 0) {
                return byStart;
            }
            return a.id().compareTo(b.id()) < 0 ? -1 : 1;
        });
        return new PackageRegister(sorted);
    }

    public Optional<WorkPackage> find(String id) {
        return packages.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public WorkPackage require(String id) {
        return find(id).orElseThrow(() -> new NotFoundException("work package", id));
    }

    public List<WorkPackage> inSection(String section) {
        return packages.stream().filter(p -> p.section().equals(section)).toList();
    }

    /** The sections the packages are grouped into, sorted. */
    public List<String> sections() {
        return List.copyOf(
                new TreeSet<>(packages.stream().map(WorkPackage::section).toList()));
    }

    /** Every package covering {@code station}. */
    public List<WorkPackage> at(long station) {
        return packages.stream().filter(p -> p.extent().contains(station)).toList();
    }

    /** Every package earning against {@code code}. */
    public List<WorkPackage> forCode(ItemCode code) {
        String key = code.key();
        return packages.stream()
                .filter(p ->
                        p.allocations().stream().anyMatch(a -> a.code().key().equals(key)))
                .toList();
    }

    /** Everything the packages together allocate against one bill item. */
    public Optional<Quantity> allocatedQuantity(ItemCode code) {
        String key = code.key();
        List<Quantity> parts = packages.stream()
                .flatMap(p -> p.allocations().stream())
                .filter(a -> a.code().key().equals(key))
                .map(PackageAllocation::quantity)
                .toList();
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Quantity.sum(parts, parts.get(0).unit()));
    }

    /**
     * Check the package register against the bill.
     *
     * <p>Three things go wrong in practice and all three are silent until a valuation comes out
     * short. Packages can allocate more than the bill carries, they can allocate less and leave
     * work with nowhere to book it, and they can allocate against an item that no longer exists
     * after a revision.
     */
    public List<ReconciliationLine> reconcileWith(Bill bill) {
        List<ReconciliationLine> lines = new ArrayList<>();
        Set<String> allocatedKeys = new LinkedHashSet<>();
        for (WorkPackage pkg : packages) {
            for (PackageAllocation allocation : pkg.allocations()) {
                allocatedKeys.add(allocation.code().key());
            }
        }

        for (String key : allocatedKeys) {
            ItemCode code = packages.stream()
                    .flatMap(p -> p.allocations().stream())
                    .filter(a -> a.code().key().equals(key))
                    .findFirst()
                    .orElseThrow()
                    .code();
            Optional<BillItem> item = bill.find(code);
            Optional<Quantity> allocated = allocatedQuantity(code);
            if (item.isEmpty()) {
                lines.add(new ReconciliationLine(
                        code,
                        Problem.UNKNOWN_ITEM,
                        Optional.empty(),
                        allocated,
                        code.format() + " is allocated but the bill has no such item"));
                continue;
            }
            Quantity billed = item.get().quantity();
            Quantity total = allocated.orElseThrow();
            int difference = total.compareTo(billed);
            if (difference > 0) {
                lines.add(new ReconciliationLine(
                        code,
                        Problem.OVER_ALLOCATED,
                        Optional.of(billed),
                        allocated,
                        code.format() + " is allocated " + total.format() + " against a billed " + billed.format()));
            } else if (difference < 0) {
                lines.add(new ReconciliationLine(
                        code,
                        Problem.UNDER_ALLOCATED,
                        Optional.of(billed),
                        allocated,
                        code.format() + " is allocated " + total.format() + " of a billed " + billed.format()));
            }
        }

        for (BillItem item : bill.items()) {
            if (!item.isEarnable() || allocatedKeys.contains(item.code().key())) {
                continue;
            }
            lines.add(new ReconciliationLine(
                    item.code(),
                    Problem.NOT_ALLOCATED,
                    Optional.of(item.quantity()),
                    Optional.empty(),
                    item.code().format() + " is in the bill but no package earns against it"));
        }

        lines.sort((a, b) -> a.code().compareTo(b.code()));
        return List.copyOf(lines);
    }

    /** Stretches of {@code extent} that no package on the alignment covers, which is usually a gap. */
    public List<ChainageRange> uncoveredStretches(ChainageRange extent, String alignmentId) {
        List<ChainageRange> covered = ChainageRange.union(onAlignment(alignmentId));
        List<ChainageRange> remaining = List.of(extent);
        for (ChainageRange range : covered) {
            remaining =
                    remaining.stream().flatMap(r -> r.subtract(range).stream()).toList();
        }
        return remaining;
    }

    /** The length the packages on the alignment cover, counting overlap once. */
    public long coveredExtent(String alignmentId) {
        return ChainageRange.coveredLength(onAlignment(alignmentId));
    }

    private List<ChainageRange> onAlignment(String alignmentId) {
        return packages.stream()
                .filter(p -> p.alignmentId().equals(alignmentId))
                .map(WorkPackage::extent)
                .toList();
    }

    /** Pairs of packages that overlap and share a bill item, in register order. */
    public List<Clash> clashes() {
        List<Clash> out = new ArrayList<>();
        for (int i = 0; i < packages.size(); i += 1) {
            for (int j = i + 1; j < packages.size(); j += 1) {
                WorkPackage left = packages.get(i);
                WorkPackage right = packages.get(j);
                if (left.clashesWith(right)) {
                    out.add(new Clash(left, right));
                }
            }
        }
        return List.copyOf(out);
    }
}
