package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The early warning register as the page that goes to a risk reduction meeting. */
public final class RiskReport {

    private RiskReport() {}

    /**
     * The register position as plain text.
     *
     * <p>Ordered by expected cost, which is the order the meeting should work through, and never by
     * reference. A register sorted by reference number is sorted by the order the risks happened to
     * be noticed in, which is the one order that carries no information at all.
     */
    public static String render(EarlyWarning.Position position) {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("early warnings at " + SiteDates.format(position.asAt())));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("open", String.valueOf(position.open().size())),
                Map.entry("expected cost", position.expected().format()),
                Map.entry("worst case", position.worstCase().format()),
                Map.entry("carried by the contractor", position.carried().format()),
                Map.entry("working days at risk", String.valueOf(position.delayDays())),
                Map.entry(
                        "meetings overdue",
                        String.valueOf(position.overdueMeetings().size())),
                Map.entry("owner not agreed", String.valueOf(position.unowned().size())))));

        if (!position.open().isEmpty()) {
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("warning"),
                            Column.of("description").withWidth(38),
                            Column.of("owner"),
                            Column.of("where").withWidth(24),
                            Column.right("if it happens"),
                            Column.right("likely"),
                            Column.right("expected"),
                            Column.right("days")),
                    position.open().stream()
                            .map(warning -> List.of(
                                    warning.reference(),
                                    warning.description(),
                                    warning.owner().wire(),
                                    warning.extent().map(ChainageRange::format).orElse(""),
                                    warning.costIfItHappens().format(),
                                    warning.likelihood().format(0),
                                    warning.expectedCost().format(),
                                    String.valueOf(warning.delayDays())))
                            .toList()));
        }
        return String.join("\n", sections);
    }

    /** The meetings somebody has to arrange, oldest warning first. */
    public static List<String> meetingsToArrange(EarlyWarning.Position position) {
        return position.overdueMeetings().stream()
                .sorted(Comparator.comparing(EarlyWarning::raisedOn))
                .map(warning -> warning.reference() + " raised " + SiteDates.format(warning.raisedOn()) + ", "
                        + (warning.meetingOn().isEmpty() ? "no meeting held" : "meeting held late"))
                .toList();
    }
}
