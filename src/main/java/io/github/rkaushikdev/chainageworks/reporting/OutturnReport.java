package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.valuation.FinalAccount;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The anticipated final account as a page.
 *
 * <p>The moving lines are limited to the worst few, because the point of the list is to be read. A
 * final account on a road job has several hundred items and nearly all of them are within a few
 * pounds of the bill, so printing them all hides the six that matter behind the ones that do not.
 */
public final class OutturnReport {

    /** How many moving lines the page lists when nobody says. */
    public static final int DEFAULT_WORST = 10;

    private OutturnReport() {}

    /** {@link #render(Outturn, int)} listing the {@link #DEFAULT_WORST} moving lines. */
    public static String render(Outturn outturn) {
        return render(outturn, DEFAULT_WORST);
    }

    /**
     * The parts and the forecast total, the position against the contract sum, then the {@code
     * worst} items moving furthest from the bill. The moving items are left out altogether when none
     * have moved. A negative {@code worst} lists all but that many of the moving items.
     */
    public static String render(Outturn outturn, int worst) {
        List<List<String>> parts = new ArrayList<>();
        for (Outturn.Part part : outturn.parts()) {
            parts.add(List.of(part.name(), part.amount().format()));
        }
        parts.add(List.of("forecast outturn", outturn.total().format()));

        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("anticipated final account at " + SiteDates.format(outturn.asAt())));
        sections.add("");
        sections.add(Table.render(List.of(Column.of("item"), Column.right("amount")), parts));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("contract sum", outturn.contractSum().format()),
                Map.entry("movement", outturn.movement().format()),
                Map.entry("movement percent", outturn.movementPercent().format(2)),
                Map.entry("position", outturn.isOverContractSum() ? "over the contract sum" : "inside it"),
                Map.entry("parts add up", outturn.partsAddUp() ? "yes" : "no"),
                Map.entry(
                        "price adjustment",
                        outturn.priceAdjustment().provisional() ? "on a provisional index" : "firm"))));

        List<FinalAccount.Line> all = outturn.account().movingLines();
        int end = worst < 0 ? Math.max(all.size() + worst, 0) : Math.min(worst, all.size());
        List<FinalAccount.Line> moving = all.subList(0, end);
        if (!moving.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("items moving away from the bill", "-"));
            sections.add(itemTable(moving, false));
        }

        // Listed in full whatever the worst count says: each one is a rate somebody has to agree.
        List<FinalAccount.Line> rerate = outturn.rerateCandidates();
        if (!rerate.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("items that may need a new rate", "-"));
            sections.add(itemTable(rerate, true));
        }
        return String.join("\n", sections);
    }

    /**
     * The items as a table: what was billed, what is forecast, and how the forecast was reached,
     * with the quantity movement as well when the table is the one reopening rates.
     */
    private static String itemTable(List<FinalAccount.Line> lines, boolean quantity) {
        List<Column> columns = new ArrayList<>(List.of(
                Column.of("code"),
                Column.of("description").withWidth(34),
                Column.right("billed"),
                Column.right("forecast"),
                Column.right("movement")));
        if (quantity) {
            columns.add(Column.right("quantity"));
        }
        columns.add(Column.right("complete"));
        columns.add(Column.of("basis"));
        List<List<String>> rows = new ArrayList<>();
        for (FinalAccount.Line line : lines) {
            List<String> row = new ArrayList<>(List.of(
                    line.code().format(),
                    line.description(),
                    line.billed().format(),
                    line.forecast().format(),
                    line.movement().format()));
            if (quantity) {
                row.add((line.movement().isNegative() ? "-" : "+")
                        + line.quantityMovement().format(1));
            }
            row.add(Numbers.format(line.percentComplete()) + "%");
            row.add(line.extrapolated() ? "trend" : "bill");
            rows.add(row);
        }
        return Table.render(columns, rows);
    }
}
