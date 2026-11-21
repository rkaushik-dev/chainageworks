package io.github.rkaushikdev.chainageworks.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.app.MonthEndPack;
import io.github.rkaushikdev.chainageworks.app.PackFile;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.reporting.CsvExport;
import io.github.rkaushikdev.chainageworks.reporting.format.Markdown;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Model.CommandSpec;

/** The commands that write a valuation out as files: the markdown pack, a csv table, the month end. */
final class ExportCommands {

    /**
     * The tables the csv command writes, one table a file and never a workbook.
     *
     * <p>Everything downstream of this is a pivot table somebody built once and refreshes every month,
     * and a file whose columns move breaks it silently. The five tables are therefore fixed, and a new
     * one gets a new name rather than a new column on an old one.
     */
    private static final Map<String, CsvTable> CSV_TABLES = csvTables();

    private static final String KNOWN_TABLES = String.join(", ", CSV_TABLES.keySet());

    private ExportCommands() {}

    /** One csv table, built at a valuation. */
    @FunctionalInterface
    private interface CsvTable {
        String build(Service service, int valuation);
    }

    private static Map<String, CsvTable> csvTables() {
        Map<String, CsvTable> tables = new LinkedHashMap<>();
        tables.put(
                "bill",
                (service, valuation) -> CsvExport.bill(
                        service.context(), service.period(valuation).cutOff()));
        tables.put(
                "records",
                (service, valuation) -> CsvExport.records(
                        service.context(), service.period(valuation).cutOff()));
        tables.put("certificate", (service, valuation) -> CsvExport.certificate(service.certificateFor(valuation)));
        tables.put("totals", (service, valuation) -> CsvExport.certificateTotals(service.certificateFor(valuation)));
        tables.put("outturn", (service, valuation) -> CsvExport.outturn(service.outturnFor(valuation)));
        return Collections.unmodifiableMap(tables);
    }

    /**
     * Write the valuation pack out as a set of markdown files.
     *
     * <p>One file a report rather than one long document, because the three go to three different
     * people. The client gets the certificate, the project manager gets the progress report, and only
     * the commercial team ever wants the subcontract position.
     */
    static CommandSpec export() {
        return Declare.command(
                "export <valuation> <directory>", "write the valuation pack out as markdown", ExportCommands::export);
    }

    private static void export(Invocation call) throws IOException {
        Service service = call.load();
        int number = call.valuation(0);
        List<PackFile> files = List.of(
                new PackFile("progress.md", Markdown.progress(service.progressFor(number))),
                new PackFile("certificate.md", Markdown.certificate(service.certificateFor(number))),
                new PackFile("payments.md", Markdown.subcontract(service.paymentsFor(number))));
        List<String> written = write(call, call.argument(1), files);
        call.print(String.join("\n", written), written);
    }

    /** Write each file under {@code directory}, answering the paths written in order. */
    private static List<String> write(Invocation call, String directory, List<PackFile> files) throws IOException {
        List<String> written = new ArrayList<>();
        for (PackFile file : files) {
            String path = directory + "/" + file.name();
            call.deps().writeText(path, file.text());
            written.add(path);
        }
        return List.copyOf(written);
    }

    static CommandSpec csv() {
        return Declare.command(
                "csv <table> <valuation> <file>", "write a table out as csv: " + KNOWN_TABLES, ExportCommands::csv);
    }

    private static void csv(Invocation call) throws IOException {
        String table = call.argument(0);
        CsvTable build = CSV_TABLES.get(table);
        if (build == null) {
            throw new BadInputException("no such table", Details.of("table", table, "known", KNOWN_TABLES));
        }
        Service service = call.load();
        String text = build.build(service, call.valuation(1));
        String file = call.argument(2);
        call.deps().writeText(file, text);
        ObjectNode summary = Json.object();
        summary.put("file", file);
        summary.put("rows", Text.split(Text.trimEnd(text), "\n").size() - 1);
        call.print(file, summary);
    }

    /**
     * The whole month in one command.
     *
     * <p>Everything here can be run one report at a time, and on a normal month somebody does. This
     * exists for the twenty fifth, when the same files have to come out in the same order with nothing
     * forgotten, and the person running it has other things to think about.
     */
    static CommandSpec monthEnd() {
        return Declare.command(
                "month-end <valuation> <directory>",
                "run the whole month end and write the pack out",
                ExportCommands::monthEnd);
    }

    private static void monthEnd(Invocation call) throws IOException {
        MonthEndPack pack = MonthEndPack.build(call.load(), call.valuation(0));
        List<String> written = write(call, call.argument(1), pack.files());
        List<String> lines = new ArrayList<>();
        lines.add(pack.describe());
        lines.addAll(written);
        ObjectNode summary = Json.object();
        summary.put("valuation", pack.valuation());
        summary.put("cutOff", pack.cutOff());
        summary.put("amountDue", pack.amountDue());
        summary.put("sound", pack.isSound());
        summary.put("errors", pack.diagnosis().errors());
        summary.set("files", Json.valueOf(written));
        call.print(String.join("\n", lines), summary);
    }
}
