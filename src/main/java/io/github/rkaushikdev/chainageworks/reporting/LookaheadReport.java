package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.progress.StepLookahead;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The lookahead as the sheet a site manager marks up on a Monday, and the inspections to chase. */
public final class LookaheadReport {

    private static final List<Column> COLUMNS = List.of(
            Column.of("package"),
            Column.of("step"),
            Column.of("where").withWidth(30),
            Column.right("metres"),
            Column.right("days"),
            Column.of("state").withWidth(28));

    private LookaheadReport() {}

    private static String extentOf(StepLookahead step) {
        return step.remaining().stream().map(ChainageRange::format).collect(Collectors.joining(", "));
    }

    private static String stateOf(StepLookahead step) {
        if (!step.heldPoints().isEmpty()) {
            return "held by " + String.join(", ", step.heldPoints());
        }
        if (!step.waitingOn().isEmpty()) {
            return "after " + String.join(", ", step.waitingOn());
        }
        return "ready";
    }

    private static List<List<String>> rows(List<StepLookahead> steps) {
        return steps.stream()
                .map(step -> List.of(
                        step.packageId(),
                        step.stepId(),
                        extentOf(step),
                        Numbers.toFixed(step.remainingMetres(), 0),
                        step.days().isPresent() ? Numbers.toFixed(step.days().getAsDouble(), 1) : "",
                        stateOf(step)))
                .toList();
    }

    /**
     * The lookahead as plain text.
     *
     * <p>Ready work first, then work waiting on somebody else, because those two lists go to
     * different people. The first is a list of gangs to allocate and the second is a list of phone
     * calls to make.
     */
    public static String render(Lookahead lookahead) {
        List<StepLookahead> ready = lookahead.readyNow();
        List<StepLookahead> held = lookahead.waitingOnInspection();

        List<String> sections = new ArrayList<>();
        sections.add(Table.heading(
                "lookahead " + SiteDates.format(lookahead.from()) + " to " + SiteDates.format(lookahead.to())));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("working days in the window", String.valueOf(lookahead.workingDays())),
                Map.entry("days of work in front", Numbers.toFixed(lookahead.daysOfWork(), 1)),
                Map.entry("fits the window", lookahead.fitsInWindow() ? "yes" : "no"),
                Map.entry("ready to start", String.valueOf(ready.size())),
                Map.entry("waiting on an inspection", String.valueOf(held.size())),
                Map.entry(
                        "no allowance set", String.valueOf(lookahead.unpriced().size())))));

        if (!ready.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("ready to start", "-"));
            sections.add(Table.render(COLUMNS, rows(ready)));
        }
        List<StepLookahead> rest =
                lookahead.steps().stream().filter(step -> !ready.contains(step)).toList();
        if (!rest.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("waiting", "-"));
            sections.add(Table.render(COLUMNS, rows(rest)));
        }
        if (lookahead.steps().isEmpty()) {
            sections.add("");
            sections.add("nothing left to build");
        }
        return String.join("\n", sections);
    }

    /** The phone calls: who has to sign what before a gang can move on. */
    public static List<String> chaseInspections(Lookahead lookahead) {
        return lookahead.waitingOnInspection().stream()
                .map(step -> step.packageId() + " " + step.stepId() + " at " + extentOf(step) + ", held by "
                        + String.join(", ", step.heldPoints()))
                .toList();
    }
}
