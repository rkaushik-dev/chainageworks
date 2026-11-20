package io.github.rkaushikdev.chainageworks.domain.packages;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A stretch of the works built and valued as one: where it is, which rule of credit it earns by,
 * and which shares of the bill it carries.
 *
 * @param id the package's key
 * @param name what it is called
 * @param alignmentId the alignment it is set out on
 * @param extent the stretch it covers
 * @param section free text grouping, usually the contract section
 * @param profileId which rule of credit decides how it earns
 * @param allocations its shares of the bill
 */
public record WorkPackage(
        String id,
        String name,
        String alignmentId,
        ChainageRange extent,
        String section,
        String profileId,
        List<PackageAllocation> allocations) {

    public WorkPackage {
        allocations = List.copyOf(allocations);
    }

    /**
     * Build a package, refusing a blank id, a package with nothing allocated, one bill item
     * allocated twice and a negative allocation.
     */
    public static WorkPackage build(WorkPackageInput input) {
        if (Text.trim(input.id()).isEmpty()) {
            throw new BadInputException("a work package needs an id");
        }
        if (input.allocations().isEmpty()) {
            throw new BadInputException(
                    "a work package with no allocation can never earn", Details.of("package", input.id()));
        }
        Set<String> seen = new HashSet<>();
        for (PackageAllocation allocation : input.allocations()) {
            if (!seen.add(allocation.code().key())) {
                throw new BadInputException(
                        "the same bill item is allocated twice to one package",
                        Details.of(
                                "package", input.id(), "code", allocation.code().format()));
            }
            if (allocation.quantity().milli() < 0) {
                throw new BadInputException(
                        "an allocated quantity cannot be negative",
                        Details.of(
                                "package", input.id(), "code", allocation.code().format()));
            }
        }
        return new WorkPackage(
                input.id(),
                input.name(),
                input.alignmentId(),
                input.extent(),
                input.section(),
                input.profileId(),
                input.allocations());
    }

    public Optional<PackageAllocation> findAllocation(ItemCode code) {
        return allocations.stream().filter(a -> a.code().equals(code)).findFirst();
    }

    public PackageAllocation requireAllocation(ItemCode code) {
        return findAllocation(code)
                .orElseThrow(() -> new NotFoundException("package allocation", id + "/" + code.format()));
    }

    /** The codes the package earns against, in allocation order. */
    public List<ItemCode> allocatedCodes() {
        return allocations.stream().map(PackageAllocation::code).toList();
    }

    /** Length of the extent, in millimetres. */
    public long length() {
        return extent.length();
    }

    /** Whether the half open extent takes in {@code station}. */
    public boolean covers(long station) {
        return extent.contains(station);
    }

    /**
     * Two packages clash when they are on the same alignment, share length, and allocate the same
     * bill item. It is not always wrong, since drainage runs under a pavement, but it is always
     * worth reporting, because it is also how the same work gets claimed twice.
     */
    public boolean clashesWith(WorkPackage other) {
        if (id.equals(other.id)) {
            return false;
        }
        if (!alignmentId.equals(other.alignmentId)) {
            return false;
        }
        if (!extent.overlaps(other.extent)) {
            return false;
        }
        return allocations.stream().anyMatch(left -> other.allocations.stream()
                .anyMatch(right -> left.code().equals(right.code())));
    }

    /** What the package is worth at the bill rates, before anything is built. */
    public Money budget(Bill bill) {
        List<Money> values = new ArrayList<>();
        for (PackageAllocation allocation : allocations) {
            BillItem item = bill.find(allocation.code())
                    .orElseThrow(() ->
                            new NotFoundException("bill item", allocation.code().format()));
            Unit.assertSame(item.rate().unit(), allocation.quantity().unit());
            values.add(item.rate().extend(allocation.quantity()));
        }
        return Money.sum(values, bill.currency());
    }

    /** {@code WP-01 earthworks [12+000.000 to 12+800.000]}, like a package register line. */
    public String describe() {
        return id + " " + name + " [" + extent.format() + "]";
    }
}
