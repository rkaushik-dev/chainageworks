package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrInput;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrStatus;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffInput;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffOutcome;
import io.github.rkaushikdev.chainageworks.io.Csv;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Reads the inspection register and the non conformance register. */
public final class InspectionImport {

    /** The columns an inspection register export has to carry. */
    public static final List<String> SIGN_OFF_COLUMNS =
            List.of("sign off", "point", "package", "from", "to", "signed on", "by");

    /** The columns a non conformance register export has to carry. */
    public static final List<String> NCR_COLUMNS = List.of("ncr", "package", "from", "to", "raised on", "description");

    private static final List<String> OUTCOMES =
            Arrays.stream(SignOffOutcome.values()).map(SignOffOutcome::wire).toList();
    private static final List<String> STATUSES =
            Arrays.stream(NcrStatus.values()).map(NcrStatus::wire).toList();

    private InspectionImport() {}

    /** Read an inspection register export. */
    public static ImportReport<SignOff> importSignOffs(String text) {
        return Rows.readTable(
                text,
                Rows.RowImport.of(SIGN_OFF_COLUMNS, (reader, issues) -> readSignOff(reader))
                        .keyedBy(SignOff::id)
                        .duplicateSays(id -> "sign off " + id + " appears twice")
                        .keyColumn("sign off"));
    }

    private static SignOff readSignOff(Csv.RowReader reader) {
        Optional<String> note = Fields.optional(reader, "note");
        Optional<String> outcome = Fields.optional(reader, "outcome");
        String id = Fields.need(reader, "sign off");
        String pointId = Fields.need(reader, "point");
        String packageId = Fields.need(reader, "package");
        var extent = Fields.needExtent(reader);
        var signedOn = Fields.needDate(reader, "signed on");
        String by = Fields.need(reader, "by");
        SignOffOutcome read = outcome.isEmpty()
                ? null
                : SignOffOutcome.fromWire(Fields.needOneOf(reader, "outcome", OUTCOMES))
                        .orElseThrow();
        LocalDate notice = Fields.optionalDate(reader, "notice given").orElse(null);
        return SignOff.build(
                new SignOffInput(id, pointId, packageId, extent, signedOn, by, read, note.orElse(null), notice));
    }

    /** Read a non conformance register export. */
    public static ImportReport<Ncr> importNcrs(String text) {
        return Rows.readTable(
                text,
                Rows.RowImport.of(NCR_COLUMNS, (reader, issues) -> readNcr(reader))
                        .keyedBy(Ncr::id)
                        .duplicateSays(id -> "report " + id + " appears twice")
                        .keyColumn("ncr"));
    }

    private static Ncr readNcr(Csv.RowReader reader) {
        Optional<String> status = Fields.optional(reader, "status");
        Optional<String> stepId = Fields.optional(reader, "step");
        String id = Fields.need(reader, "ncr");
        String packageId = Fields.need(reader, "package");
        var extent = Fields.needExtent(reader);
        var raisedOn = Fields.needDate(reader, "raised on");
        var settledOn = Fields.optionalDate(reader, "settled on");
        String description = Fields.need(reader, "description");
        boolean withholds = Fields.needFlag(reader, "withholds credit", true);
        NcrStatus read = status.isEmpty()
                ? null
                : NcrStatus.fromWire(Fields.needOneOf(reader, "status", STATUSES))
                        .orElseThrow();
        return Ncr.build(new NcrInput(
                id,
                packageId,
                extent,
                stepId.orElse(null),
                raisedOn,
                settledOn.orElse(null),
                read,
                description,
                withholds));
    }
}
