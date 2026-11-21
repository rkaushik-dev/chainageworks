package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.earthworks.Movement;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The haul plan as the sheet that goes to the plant manager, and the muck away schedule. */
public final class HaulReport {

    private HaulReport() {}

    /**
     * The haul plan as plain text.
     *
     * <p>Ordered by what it costs rather than by where it is on the alignment, because the first
     * three lines are where the money is and a plan sorted by chainage buries them in the middle.
     */
    public static String render(HaulAllocation allocation, List<HaulSite> sites) {
        Map<String, String> nameOf = new HashMap<>();
        sites.forEach(site -> nameOf.put(site.id(), site.name()));
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("haul plan"));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("movements", String.valueOf(allocation.movements().size())),
                Map.entry("haul moment", Numbers.toFixed(allocation.haulMoment(), 0) + " m3 km"),
                Map.entry("cost", allocation.cost().format()),
                Map.entry("balanced", allocation.isBalanced() ? "yes" : "no"),
                Map.entry("unplaced", allocation.unplaced().format()),
                Map.entry("unfilled", allocation.unfilled().format()),
                Map.entry("spare tip capacity", allocation.spareTipCapacity().format()))));

        if (!allocation.movements().isEmpty()) {
            List<Movement> ordered = allocation.movements().stream()
                    .sorted(Comparator.comparingLong((Movement m) -> m.cost().minor())
                            .reversed())
                    .toList();
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("from").withWidth(26),
                            Column.of("to").withWidth(26),
                            Column.right("volume"),
                            Column.right("km"),
                            Column.right("haul"),
                            Column.right("site"),
                            Column.right("cost")),
                    ordered.stream()
                            .map(movement -> List.of(
                                    Text.trim(movement.fromId() + " " + nameOf.getOrDefault(movement.fromId(), "")),
                                    Text.trim(movement.toId() + " " + nameOf.getOrDefault(movement.toId(), "")),
                                    movement.volume().format(),
                                    Numbers.format(movement.km()),
                                    movement.haulCost().format(),
                                    movement.siteCost().format(),
                                    movement.cost().format()))
                            .toList()));
        }
        return String.join("\n", sections);
    }

    /** What has to leave each source, which is what a muck away schedule is. */
    public static List<String> muckAwaySchedule(HaulAllocation allocation, List<HaulSite> sites) {
        return sites.stream()
                .filter(site -> site.kind() == SiteKind.CUT || site.kind() == SiteKind.BORROW)
                .map(site -> {
                    List<Movement> out = allocation.movementsFrom(site.id());
                    double total = 0;
                    for (Movement movement : out) {
                        total += movement.volume().amount();
                    }
                    String to = out.stream().map(Movement::toId).collect(Collectors.joining(", "));
                    return out.isEmpty()
                            ? site.id() + " " + site.name() + ", nowhere to go"
                            : site.id() + " " + site.name() + ", " + Numbers.format(total) + " m3 to " + to;
                })
                .toList();
    }
}
