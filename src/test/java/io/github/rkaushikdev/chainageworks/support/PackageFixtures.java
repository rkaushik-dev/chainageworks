package io.github.rkaushikdev.chainageworks.support;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackageInput;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import java.util.List;

/** Work packages for tests. */
public final class PackageFixtures {

    private PackageFixtures() {}

    /**
     * One allocation of a test package.
     *
     * @param code the item code as written
     * @param amount the allocated amount
     * @param unit its unit
     */
    public record Share(String code, double amount, Unit unit) {}

    public static Share share(String code, double amount, Unit unit) {
        return new Share(code, amount, unit);
    }

    /**
     * A test package. Name, profile, section and alignment have defaults.
     *
     * @param id the package id
     * @param name the name, or null for {@code package <id>}
     * @param from the first station as written
     * @param to the last station as written
     * @param profileId the rule of credit, or null for {@code single-step}
     * @param section the section, or null for {@code section 1}
     * @param alignmentId the alignment, or null for {@code A1}
     * @param allocations its shares of the bill
     */
    public record PackageSpec(
            String id,
            String name,
            String from,
            String to,
            String profileId,
            String section,
            String alignmentId,
            List<Share> allocations) {

        public static PackageSpec of(String id, String from, String to, Share... allocations) {
            return new PackageSpec(id, null, from, to, null, null, null, List.of(allocations));
        }

        public PackageSpec named(String text) {
            return new PackageSpec(id, text, from, to, profileId, section, alignmentId, allocations);
        }

        public PackageSpec profile(String text) {
            return new PackageSpec(id, name, from, to, text, section, alignmentId, allocations);
        }

        public PackageSpec section(String text) {
            return new PackageSpec(id, name, from, to, profileId, text, alignmentId, allocations);
        }

        public PackageSpec alignment(String text) {
            return new PackageSpec(id, name, from, to, profileId, section, text, allocations);
        }
    }

    public static WorkPackage pkg(PackageSpec spec) {
        return WorkPackage.build(new WorkPackageInput(
                spec.id(),
                spec.name() == null ? "package " + spec.id() : spec.name(),
                spec.alignmentId() == null ? "A1" : spec.alignmentId(),
                ChainageRange.of(Stations.parse(spec.from()), Stations.parse(spec.to())),
                spec.section() == null ? "section 1" : spec.section(),
                spec.profileId() == null ? "single-step" : spec.profileId(),
                spec.allocations().stream()
                        .map(s -> new PackageAllocation(ItemCode.parse(s.code()), Quantity.of(s.amount(), s.unit())))
                        .toList()));
    }

    /**
     * Three packages down the alignment, splitting the sample bill between them. The earthworks
     * split is deliberately short of the billed quantity, so the reconciliation has something to
     * report.
     */
    public static final List<PackageSpec> SAMPLE_PACKAGES = List.of(
            PackageSpec.of("WP-01", "12+000", "12+800", share("E423.1", 7000, Unit.M3), share("E532.1", 5000, Unit.M3))
                    .named("earthworks, chainage 12+000 to 12+800")
                    .profile("bulk-earthworks"),
            PackageSpec.of(
                            "WP-02",
                            "12+800",
                            "13+600",
                            share("E423.1", 5000, Unit.M3),
                            share("E423.2", 3200, Unit.M3),
                            share("E532.1", 4800, Unit.M3))
                    .named("earthworks, chainage 12+800 to 13+600")
                    .profile("bulk-earthworks"),
            PackageSpec.of("WP-03", "12+000", "13+600", share("R712.1", 4000, Unit.M2), share("R712.2", 4000, Unit.M2))
                    .named("pavement, chainage 12+000 to 13+600")
                    .section("section 2")
                    .profile("pavement"));

    public static List<WorkPackage> samplePackages() {
        return SAMPLE_PACKAGES.stream().map(PackageFixtures::pkg).toList();
    }
}
