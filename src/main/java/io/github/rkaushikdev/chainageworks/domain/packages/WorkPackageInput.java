package io.github.rkaushikdev.chainageworks.domain.packages;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.util.List;

/**
 * What a work package is built from. A {@code null} section means none.
 *
 * @param id the package's key
 * @param name what it is called
 * @param alignmentId the alignment it is set out on
 * @param extent the stretch it covers
 * @param section free text grouping, usually the contract section
 * @param profileId which rule of credit decides how it earns
 * @param allocations its shares of the bill
 */
public record WorkPackageInput(
        String id,
        String name,
        String alignmentId,
        ChainageRange extent,
        String section,
        String profileId,
        List<PackageAllocation> allocations) {

    public WorkPackageInput {
        section = section == null ? "" : section;
        allocations = List.copyOf(allocations);
    }
}
