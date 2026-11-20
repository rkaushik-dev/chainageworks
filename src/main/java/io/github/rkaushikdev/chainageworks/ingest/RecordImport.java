package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.domain.survey.MeasurementMethod;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecordInput;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Reads a survey book export. */
public final class RecordImport {

    /** The columns a survey book export has to carry. */
    public static final List<String> RECORD_COLUMNS =
            List.of("record", "package", "code", "step", "from", "to", "quantity", "unit", "measured on", "booked by");

    private static final Pattern SEPARATORS = Patterns.compile("[\\s-]+");

    private RecordImport() {}

    /**
     * Read a survey book export.
     *
     * <p>Every row is read on its own and a bad one is left out with an issue rather than stopping
     * the run. A site sends three hundred rows at a time and two of them are always wrong; failing
     * the whole file means the other two hundred and ninety eight wait a day.
     */
    public static ImportReport<SurveyRecord> importSurveyRecords(String text) {
        return Rows.readTable(
                text,
                Rows.RowImport.of(RECORD_COLUMNS, RecordImport::readRow)
                        .keyedBy(SurveyRecord::id)
                        .duplicateSays(id -> "record " + id + " appears twice")
                        .keyColumn("record"));
    }

    private static SurveyRecord readRow(Csv.RowReader reader, List<ImportIssue> issues) {
        Optional<String> supersedes = Fields.optional(reader, "supersedes");
        Optional<String> note = Fields.optional(reader, "note");
        return SurveyRecord.build(new SurveyRecordInput(
                Fields.need(reader, "record"),
                Fields.need(reader, "package"),
                Fields.needCode(reader, "code"),
                Fields.need(reader, "step"),
                Fields.needExtent(reader),
                Fields.needQuantity(reader, "quantity", "unit"),
                Fields.needDate(reader, "measured on"),
                Fields.need(reader, "booked by"),
                readMethod(reader, issues),
                supersedes.orElse(null),
                note.orElse(null)));
    }

    /**
     * An unrecognised method is a warning, not a rejection. The quantity is still the quantity, and
     * the method only matters when somebody audits the row.
     */
    private static MeasurementMethod readMethod(Csv.RowReader reader, List<ImportIssue> issues) {
        Optional<String> text = Fields.optional(reader, "method");
        if (text.isEmpty()) {
            return MeasurementMethod.TAPE;
        }
        String cleaned = SEPARATORS.matcher(Text.lower(Text.trim(text.get()))).replaceAll("_");
        Optional<MeasurementMethod> found = MeasurementMethod.fromWire(cleaned);
        if (found.isEmpty()) {
            issues.add(ImportIssue.warning(
                    "method " + text.get() + " was not recognised, taking a tape", reader.line(), "method"));
            return MeasurementMethod.TAPE;
        }
        return found.get();
    }
}
