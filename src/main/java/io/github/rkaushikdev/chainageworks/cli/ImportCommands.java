package io.github.rkaushikdev.chainageworks.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.ingest.ImportIssue;
import io.github.rkaushikdev.chainageworks.ingest.ImportReport;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.io.IOException;
import java.util.List;
import picocli.CommandLine.Model.CommandSpec;

/**
 * The commands that load a site or commercial export into the job.
 *
 * <p>Every import is the same three steps: read the file, hand it to the service, and save if the job
 * took the rows and auto save is on. Written out one at a time they invite a command that quietly
 * forgets the save.
 */
final class ImportCommands {

    private static final String PARTIAL = "--partial";

    private ImportCommands() {}

    /** The import commands, in the order the tree lists them. */
    static List<CommandSpec> all() {
        return List.of(
                command("records", "load a survey book export", Service::loadRecords, "records"),
                command("sign-offs", "load an inspection register export", Service::loadSignOffs, "sign offs"),
                command("ncrs", "load a non conformance register export", Service::loadNcrs, "reports"),
                command("diary", "load the standdowns out of a site diary", Service::loadDiary, "diary lines"),
                command("holidays", "load the contract holiday list", Service::loadHolidays, "holidays"),
                command(
                        "applications",
                        "load a subcontract payment application schedule",
                        Service::loadApplications,
                        "applications"),
                command("notices", "load the payment notices served", Service::loadNotices, "notices"),
                command("indices", "load a published cost index bulletin", Service::loadIndices, "index series"),
                command(
                        "returns",
                        "load a labour and plant resource return export",
                        Service::loadResourceReturns,
                        "resource returns"),
                command(
                        "pay-less",
                        "load the pay less notices served",
                        Service::loadPayLessNotices,
                        "pay less notices"));
    }

    /** How one register is loaded: the text of the export in, the report on what it took out. */
    @FunctionalInterface
    private interface Loader {
        ImportReport<?> load(Service service, String text, boolean partial);
    }

    private static CommandSpec command(String name, String description, Loader loader, String what) {
        return Declare.command(
                "import-" + name + " <file>",
                description,
                new Import(loader, what),
                Declare.option("--partial", "take the rows that parsed even when others did not"));
    }

    /**
     * One import command.
     *
     * @param loader the service call that reads the export and applies it
     * @param what what the rows are, as the summary line counts them
     */
    private record Import(Loader loader, String what) implements Action {

        @Override
        public void run(Invocation call) throws IOException {
            Service service = call.load();
            boolean partial = call.flag(PARTIAL);
            ImportReport<?> report = loader.load(service, call.deps().readText(call.argument(0)), partial);
            if ((!report.hasErrors() || partial) && service.config().autoSave()) {
                call.deps().saveJob(service.job(), service.config().jobFile());
            }
            call.print(report.format(what), summary(report));
        }
    }

    /** What the json flag prints for an import: the counts, and every issue the import raised. */
    private static ObjectNode summary(ImportReport<?> report) {
        ObjectNode summary = Json.object();
        summary.put("rowsRead", report.rowsRead());
        summary.put("accepted", report.rowsAccepted());
        summary.put("rejected", report.rowsRejected());
        summary.set(
                "issues",
                Json.valueOf(report.issues().stream().map(ImportCommands::issue).toList()));
        return summary;
    }

    private static ObjectNode issue(ImportIssue issue) {
        ObjectNode json = Json.object();
        json.set("line", Json.valueOf(issue.line().orElse(null)));
        json.set("column", Json.valueOf(issue.column().orElse(null)));
        json.put("message", issue.message());
        json.put("severity", issue.severity().wire());
        return json;
    }
}
