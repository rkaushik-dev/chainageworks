package io.github.rkaushikdev.chainageworks.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.app.Diagnosis;
import io.github.rkaushikdev.chainageworks.app.Doctor;
import io.github.rkaushikdev.chainageworks.app.Finding;
import io.github.rkaushikdev.chainageworks.app.FindingLevel;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrStatus;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.RevisionReport;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Model.CommandSpec;

/** The commands that look at the job as a whole rather than at one valuation. */
final class JobCommands {

    private static final String AS_AT = "--as-at";
    private static final String MARKDOWN_TO = "--markdown-to";
    private static final String RATES = "--rates";
    private static final String ALL = "--all";

    private JobCommands() {}

    static CommandSpec status() {
        return Declare.command("status", "what the job holds and what it is missing", JobCommands::status);
    }

    private static void status(Invocation call) {
        Job job = call.load().job();
        List<String> blockers = job.blockers();
        String text = Table.pairs(List.of(
                Map.entry("job", job.settings().id() + " " + job.settings().name()),
                Map.entry("contract sum", job.settings().contractSum().format()),
                Map.entry("bill revisions", Integer.toString(job.billRevisions().count())),
                Map.entry("packages", Integer.toString(job.packages().count())),
                Map.entry("survey records", Integer.toString(job.records().count())),
                Map.entry("sign offs", Integer.toString(job.signOffs().count())),
                Map.entry(
                        "open reports",
                        Integer.toString(job.ncrs()
                                .where(ncr -> ncr.status() == NcrStatus.OPEN)
                                .size())),
                Map.entry("blockers", blockers.isEmpty() ? "none" : String.join("; ", blockers))));
        ObjectNode summary = Json.object();
        summary.put("id", job.settings().id());
        summary.put("name", job.settings().name());
        summary.set("blockers", Json.valueOf(blockers));
        call.print(text, summary);
    }

    static CommandSpec config() {
        return Declare.command("config", "the settings this run would use", JobCommands::config);
    }

    private static void config(Invocation call) {
        Config config = call.config();
        call.print(config.describe(), config.toJson());
    }

    static CommandSpec bill() {
        return Declare.command(
                "bill",
                "the bill in force on a date",
                JobCommands::bill,
                Declare.option("--as-at <date>", "the date to price at"));
    }

    private static void bill(Invocation call) {
        Service service = call.load();
        Optional<String> given = call.option(AS_AT);
        LocalDate asAt = given.isEmpty() ? service.schedule(1).periods().get(0).cutOff() : SiteDates.parse(given.get());
        Bill bill = service.context().billHistory().billAsAt(asAt);
        String text = Table.render(
                List.of(
                        Column.of("code"),
                        Column.of("description").withWidth(44),
                        Column.right("quantity"),
                        Column.right("rate"),
                        Column.of("kind")),
                bill.items().stream()
                        .map(item -> List.of(
                                item.code().format(),
                                item.description(),
                                item.quantity().format(),
                                item.rate().format(),
                                item.kind().wire()))
                        .toList());
        call.print(text, bill.items().size());
    }

    static CommandSpec packages() {
        return Declare.command("packages", "the work package register", JobCommands::packages);
    }

    private static void packages(Invocation call) {
        Job job = call.load().job();
        List<WorkPackage> packages = job.packages().all();
        String text = Table.render(
                List.of(
                        Column.of("package"),
                        Column.of("section"),
                        Column.of("extent"),
                        Column.of("rule of credit"),
                        Column.right("items")),
                packages.stream()
                        .map(pkg -> List.of(
                                pkg.id(),
                                pkg.section(),
                                pkg.extent().format(),
                                pkg.profileId(),
                                Integer.toString(pkg.allocations().size())))
                        .toList());
        call.print(text, job.packages().count());
    }

    static CommandSpec commands() {
        return Declare.command(
                "commands",
                "the command reference, read off the command tree itself",
                JobCommands::commands,
                Declare.option("--markdown-to <file>", "write the reference out as markdown"));
    }

    private static void commands(Invocation call) throws IOException {
        Optional<String> markdownTo = call.option(MARKDOWN_TO);
        if (markdownTo.isPresent()) {
            String file = markdownTo.get();
            call.deps().writeText(file, Reference.markdown(call.tree()));
            ObjectNode written = Json.object();
            written.put("file", file);
            call.print(file, written);
            return;
        }
        List<Reference.CommandEntry> entries = Reference.commandEntries(call.tree());
        call.print(
                String.join("\n", Reference.lines(call.tree())),
                Json.valueOf(
                        entries.stream().map(Reference.CommandEntry::toJson).toList()));
    }

    static CommandSpec revisions() {
        return Declare.command(
                "revisions",
                "what each revision of the bill did to the one before it",
                JobCommands::revisions,
                Declare.option("--rates", "print only the rate movements"));
    }

    private static void revisions(Invocation call) {
        RevisionReport report = call.load().revisionsFor();
        if (call.flag(RATES)) {
            List<String> moves = report.rateChanges();
            call.print(String.join("\n", moves), moves);
            return;
        }
        call.print(report.render(), report.steps().size());
    }

    /**
     * Every check the job can fail before anybody looks at a number.
     *
     * <p>Fails on an error so it can be the first line of a nightly run. Warnings deliberately do not,
     * because a job halfway through has packages with nothing measured against them and that is not a
     * fault.
     */
    static CommandSpec doctor() {
        return Declare.command(
                "doctor",
                "check the job for references that go nowhere",
                JobCommands::doctor,
                Declare.option("--all", "print the warnings as well as the errors"));
    }

    private static void doctor(Invocation call) {
        Diagnosis diagnosis = Doctor.diagnose(call.load().job());
        List<Finding> shown = call.flag(ALL)
                ? diagnosis.findings()
                : diagnosis.findings().stream()
                        .filter(finding -> finding.level() == FindingLevel.ERROR)
                        .toList();
        List<String> lines = new ArrayList<>();
        diagnosis.blockers().forEach(blocker -> lines.add("error job: " + blocker));
        shown.forEach(finding -> lines.add(finding.describe()));
        String text = lines.isEmpty()
                ? "nothing wrong, " + diagnosis.warnings() + " warnings not shown"
                : String.join("\n", lines);
        call.print(text, diagnosisJson(diagnosis));
        if (!diagnosis.isHealthy()) {
            throw new BadInputException("the job has errors", Details.of("errors", diagnosis.errors()));
        }
    }

    /** The diagnosis as the json flag prints it: the findings, the two counts and the blockers. */
    private static ObjectNode diagnosisJson(Diagnosis diagnosis) {
        ObjectNode json = Json.object();
        json.set(
                "findings",
                Json.valueOf(diagnosis.findings().stream()
                        .map(JobCommands::findingJson)
                        .toList()));
        json.put("errors", diagnosis.errors());
        json.put("warnings", diagnosis.warnings());
        json.set("blockers", Json.valueOf(diagnosis.blockers()));
        return json;
    }

    private static ObjectNode findingJson(Finding finding) {
        ObjectNode json = Json.object();
        json.put("level", finding.level().wire());
        json.put("register", finding.register());
        json.put("subject", finding.subject());
        json.put("message", finding.message());
        return json;
    }

    static CommandSpec reconcile() {
        return Declare.command("reconcile", "the packages against the bill", JobCommands::reconcile);
    }

    private static void reconcile(Invocation call) {
        ProgressReport report = call.load().progressFor(1);
        call.print(PlainText.reconciliation(report), report.reconciliation().size());
    }
}
