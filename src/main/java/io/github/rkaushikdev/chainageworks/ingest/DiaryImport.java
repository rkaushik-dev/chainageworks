package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the standdowns out of a site diary export, and the contract holiday list. */
public final class DiaryImport {

    /** The columns a diary export has to carry. */
    public static final List<String> DIARY_COLUMNS = List.of("date", "reason");

    /** The columns a holiday list has to carry. */
    public static final List<String> HOLIDAY_COLUMNS = List.of("date");

    private static final List<String> REASONS =
            Arrays.stream(StanddownReason.values()).map(StanddownReason::wire).toList();

    /** Site diary spellings that mean one of the reasons the calendar knows. */
    private static final Map<String, StanddownReason> REASON_ALIASES = Map.ofEntries(
            Map.entry("rain", StanddownReason.WEATHER),
            Map.entry("wet", StanddownReason.WEATHER),
            Map.entry("frost", StanddownReason.WEATHER),
            Map.entry("snow", StanddownReason.WEATHER),
            Map.entry("wind", StanddownReason.WEATHER),
            Map.entry("ground", StanddownReason.GROUND_CONDITIONS),
            Map.entry("mud", StanddownReason.GROUND_CONDITIONS),
            Map.entry("waterlogged", StanddownReason.GROUND_CONDITIONS),
            Map.entry("no_access", StanddownReason.ACCESS),
            Map.entry("blocked", StanddownReason.ACCESS),
            Map.entry("strike", StanddownReason.INDUSTRIAL),
            Map.entry("dispute", StanddownReason.INDUSTRIAL));

    private static final Pattern SEPARATORS = Patterns.compile("[\\s-]+");

    private DiaryImport() {}

    /**
     * Read the standdown lines out of a site diary export.
     *
     * <p>The reason column is free text on site, so the common spellings are mapped before the
     * strict check runs. Anything still unrecognised becomes {@code other} with a warning rather
     * than being dropped: the day was lost whatever the clerk called it, and losing the day is what
     * the delay analysis needs.
     */
    public static ImportReport<Standdown> importStanddowns(String text) {
        Csv.Table table = Csv.parse(text);
        Optional<ImportReport<Standdown>> columnProblem = ImportReport.checkColumns(table, DIARY_COLUMNS);
        if (columnProblem.isPresent()) {
            return columnProblem.get();
        }
        List<Standdown> imported = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        for (Csv.Row row : table.rows()) {
            Csv.RowReader reader = table.readerFor(row);
            try {
                LocalDate date = Fields.needDate(reader, "date");
                if (!seen.add(date)) {
                    issues.add(
                            ImportIssue.warning(SiteDates.format(date) + " is stood down twice", row.line(), "date"));
                }
                Optional<String> note = Fields.optional(reader, "note");
                imported.add(new Standdown(date, readReason(reader, issues), note));
            } catch (RuntimeException failure) {
                issues.add(ImportIssue.from(failure, reader.line()));
            }
        }
        return new ImportReport<>(imported, issues, table.rows().size());
    }

    private static StanddownReason readReason(Csv.RowReader reader, List<ImportIssue> issues) {
        String text = Fields.need(reader, "reason");
        String cleaned = SEPARATORS.matcher(Text.lower(Text.trim(text))).replaceAll("_");
        StanddownReason alias = REASON_ALIASES.get(cleaned);
        if (alias != null) {
            return alias;
        }
        try {
            return StanddownReason.fromWire(Fields.needOneOf(reader, "reason", REASONS))
                    .orElseThrow();
        } catch (BadInputException unknown) {
            issues.add(ImportIssue.warning(
                    "reason " + text + " was not recognised, filing it as other", reader.line(), "reason"));
            return StanddownReason.OTHER;
        }
    }

    /** Read the contract holiday list. One date a row, with an optional name. */
    public static ImportReport<LocalDate> importHolidays(String text) {
        Csv.Table table = Csv.parse(text);
        Optional<ImportReport<LocalDate>> columnProblem = ImportReport.checkColumns(table, HOLIDAY_COLUMNS);
        if (columnProblem.isPresent()) {
            return columnProblem.get();
        }
        List<LocalDate> imported = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        for (Csv.Row row : table.rows()) {
            Csv.RowReader reader = table.readerFor(row);
            try {
                LocalDate date = Fields.needDate(reader, "date");
                if (!seen.add(date)) {
                    issues.add(ImportIssue.warning(SiteDates.format(date) + " is listed twice", row.line(), "date"));
                    continue;
                }
                imported.add(date);
            } catch (RuntimeException failure) {
                issues.add(ImportIssue.from(failure, reader.line()));
            }
        }
        return new ImportReport<>(imported, issues, table.rows().size());
    }
}
