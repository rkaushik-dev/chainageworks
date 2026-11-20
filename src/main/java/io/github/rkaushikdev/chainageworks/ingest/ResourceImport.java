package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturnInput;
import io.github.rkaushikdev.chainageworks.domain.resource.Shift;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Reads a resource return export, one resource line to a row. */
public final class ResourceImport {

    /** The columns a resource return export has to carry. */
    public static final List<String> RESOURCE_COLUMNS =
            List.of("return", "package", "date", "resource", "kind", "count", "hours", "returned by");

    private static final List<String> HOUR_KINDS =
            Arrays.stream(HourKind.values()).map(HourKind::wire).toList();

    private ResourceImport() {}

    private enum Side {
        LABOUR,
        PLANT
    }

    private record ResourceRow(
            String returnId,
            String packageId,
            String stepId,
            LocalDate date,
            Shift shift,
            Side side,
            ResourceLine line,
            String returnedBy,
            String note) {}

    private static ResourceRow readRow(Csv.RowReader reader) {
        Optional<String> step = Fields.optional(reader, "step");
        Optional<String> shift = Fields.optional(reader, "shift");
        String returnId = Fields.need(reader, "return");
        String packageId = Fields.need(reader, "package");
        LocalDate date = Fields.needDate(reader, "date");
        Side side = Text.lower(Text.trim(Fields.need(reader, "kind"))).equals("plant") ? Side.PLANT : Side.LABOUR;
        ResourceLine line = new ResourceLine(
                Fields.need(reader, "resource"),
                Fields.optional(reader, "description").orElse(""),
                Fields.needWholeNumber(reader, "count"),
                Fields.needNumber(reader, "hours"),
                readHourKind(reader));
        return new ResourceRow(
                returnId,
                packageId,
                step.orElse(null),
                date,
                shift.filter("night"::equals).isPresent() ? Shift.NIGHT : Shift.DAY,
                side,
                line,
                Fields.need(reader, "returned by"),
                Fields.optional(reader, "note").orElse(""));
    }

    /**
     * An hours column nobody filled in means the hours were worked.
     *
     * <p>That is the right way round for a site return. A foreman writes "standing" or "idle"
     * precisely because those hours are unusual and somebody will ask about them, and leaves the
     * column alone on an ordinary day.
     */
    private static HourKind readHourKind(Csv.RowReader reader) {
        return Fields.optional(reader, "hour kind").isEmpty()
                ? HourKind.WORKING
                : HourKind.fromWire(Fields.needOneOf(reader, "hour kind", HOUR_KINDS))
                        .orElseThrow();
    }

    /**
     * Read a resource return export, one resource line to a row, grouped back into returns.
     *
     * <p>Rows are grouped by the return reference, because that is the shape a return actually has:
     * one sheet, one day, one package, and as many gang and plant lines as the foreman wrote on it.
     *
     * <p>A row whose grouping fields disagree with the first row of the same return is rejected on
     * its own rather than poisoning the return, because the usual cause is one line pasted under
     * the wrong heading. The report counts returns rather than rows.
     */
    public static ImportReport<ResourceReturn> importResourceReturns(String text) {
        return Rows.readGrouped(
                text,
                Rows.GroupedImport.<ResourceRow, ResourceReturn>of(
                                RESOURCE_COLUMNS,
                                (reader, issues) -> readRow(reader),
                                ResourceRow::returnId,
                                (id, head, rows) -> ResourceReturn.build(new ResourceReturnInput(
                                        id,
                                        head.packageId(),
                                        head.stepId(),
                                        head.date(),
                                        head.shift(),
                                        rows.stream()
                                                .filter(row -> row.side() == Side.LABOUR)
                                                .map(ResourceRow::line)
                                                .toList(),
                                        rows.stream()
                                                .filter(row -> row.side() == Side.PLANT)
                                                .map(ResourceRow::line)
                                                .toList(),
                                        head.returnedBy(),
                                        head.note())))
                        .agreeing((head, row) -> head.packageId().equals(row.packageId())
                                        && head.date().equals(row.date())
                                ? Optional.empty()
                                : Optional.of("return " + row.returnId() + " is against " + head.packageId() + " on "
                                        + SiteDates.format(head.date()) + " on one row and " + row.packageId() + " on "
                                        + SiteDates.format(row.date()) + " on another"))
                        .keyColumn("return"));
    }
}
