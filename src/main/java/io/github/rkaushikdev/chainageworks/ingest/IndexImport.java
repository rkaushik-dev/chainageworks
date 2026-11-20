package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexReading;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import java.util.List;

/** Reads a published index bulletin, one month to a row. */
public final class IndexImport {

    /** The columns an index bulletin has to carry. */
    public static final List<String> INDEX_COLUMNS = List.of("index", "year", "month", "value");

    private IndexImport() {}

    private record IndexRow(String seriesId, String name, IndexReading reading) {}

    /**
     * Read a published index bulletin, one month to a row, grouped into one series per index.
     *
     * <p>The provisional column defaults to firm, which is the safe way round. A figure wrongly held
     * provisional only causes a certificate to be revisited unnecessarily, while a provisional
     * figure wrongly taken as firm closes an account on a number that is still going to move.
     */
    public static ImportReport<IndexSeries> importIndexSeries(String text) {
        return Rows.readGrouped(
                text,
                Rows.GroupedImport.<IndexRow, IndexSeries>of(
                                INDEX_COLUMNS,
                                (reader, issues) -> new IndexRow(
                                        Fields.need(reader, "index"),
                                        Fields.optional(reader, "name").orElse(""),
                                        new IndexReading(
                                                Fields.needWholeNumber(reader, "year"),
                                                Fields.needWholeNumber(reader, "month"),
                                                Fields.needNumber(reader, "value"),
                                                Fields.needFlag(reader, "provisional", false))),
                                IndexRow::seriesId,
                                (id, head, rows) -> IndexSeries.build(
                                        id,
                                        head.name().isEmpty() ? null : head.name(),
                                        rows.stream().map(IndexRow::reading).toList()))
                        .keyColumn("index"));
    }
}
