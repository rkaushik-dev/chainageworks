package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.reporting.DefectsReport;
import io.github.rkaushikdev.chainageworks.reporting.DelayReport;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport;
import io.github.rkaushikdev.chainageworks.reporting.OutturnReport;
import io.github.rkaushikdev.chainageworks.reporting.RiskReport;
import io.github.rkaushikdev.chainageworks.reporting.StripMap;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport;
import io.github.rkaushikdev.chainageworks.reporting.VariationReport;
import io.github.rkaushikdev.chainageworks.reporting.format.Markdown;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import picocli.CommandLine.Model.CommandSpec;

/** The reports on one valuation, each answered as at that valuation's cut-off. */
final class ValuationCommands {

    private static final String NO_SUCH_VALUATION = "no such valuation";

    private ValuationCommands() {}

    /** The valuation commands, in the order the tree lists them. */
    static List<CommandSpec> all() {
        return List.of(
                report(
                        "progress",
                        "the progress report for a valuation",
                        new Report<>(
                                (service, number) -> Optional.of(service.progressFor(number)),
                                (report, service) -> PlainText.progressReport(report),
                                report -> report.period().number(),
                                NO_SUCH_VALUATION,
                                Optional.of(Markdown::progress))),
                report(
                        "certificate",
                        "the payment certificate for a valuation",
                        new Report<>(
                                (service, number) -> Optional.of(service.certificateFor(number)),
                                (certificate, service) -> PlainText.certificate(certificate),
                                certificate -> certificate.period().number(),
                                NO_SUCH_VALUATION,
                                Optional.of(Markdown::certificate))),
                strip(),
                report(
                        "delay",
                        "the extension of time position at a valuation",
                        new Report<>(
                                Service::delayFor,
                                (report, service) -> report.render(),
                                DelayReport::daysLate,
                                "the contract fixes no completion date",
                                Optional.empty())),
                report(
                        "defects",
                        "the defects position and what it blocks",
                        new Report<>(
                                Service::defectsFor,
                                (report, service) -> report.render(),
                                (DefectsReport report) -> report.summary().outstanding(),
                                "the contract fixes no defects period",
                                Optional.empty())),
                report(
                        "output",
                        "what the job produced for the hours it booked",
                        new Report<>(
                                (service, number) -> Optional.of(service.outputFor(number)),
                                (report, service) -> report.render(),
                                (OutputReport report) -> report.totals().labourHours(),
                                "no returns have been made",
                                Optional.empty())),
                report(
                        "outturn",
                        "the anticipated final account at a valuation",
                        new Report<>(
                                (service, number) -> Optional.of(service.outturnFor(number)),
                                (outturn, service) -> OutturnReport.render(
                                        outturn, service.config().reports().worstLines()),
                                outturn -> outturn.total().minor(),
                                NO_SUCH_VALUATION,
                                Optional.empty())),
                report(
                        "variations",
                        "the variation register, valued at a valuation",
                        new Report<>(
                                (service, number) -> Optional.of(service.variationsFor(number)),
                                (report, service) -> report.render(),
                                (VariationReport report) -> report.lines().size(),
                                NO_SUCH_VALUATION,
                                Optional.empty())),
                report(
                        "risks",
                        "the early warning register at a valuation",
                        new Report<>(
                                (service, number) -> Optional.of(service.risksFor(number)),
                                (position, service) -> RiskReport.render(position),
                                position -> position.open().size(),
                                NO_SUCH_VALUATION,
                                Optional.empty())),
                report(
                        "payments",
                        "the subcontract payment position and the notice diary",
                        new Report<>(
                                (service, number) -> Optional.of(service.paymentsFor(number)),
                                (report, service) -> report.render(),
                                (SubcontractReport report) -> report.positions().size(),
                                "no orders have been placed",
                                Optional.of(Markdown::subcontract))));
    }

    private static CommandSpec report(String name, String description, Report<?> report) {
        return Declare.command(name + " <valuation>", description, report);
    }

    /**
     * Every report on a valuation is the same four steps: load the job, build the report at that number,
     * say so when the contract fixes no such term, and print either the text or the summary. Written out
     * one command at a time they drift, and the two that can find no such term answer it differently.
     *
     * @param build the report at a valuation number, empty when the contract fixes no such term
     * @param render the report as text, given the service it was built from for its settings
     * @param summarise what the json flag prints instead of the text
     * @param absent what is printed when there is no report to give
     * @param markdown the report as markdown, for the report commands that have a document form
     */
    private record Report<T>(
            BiFunction<Service, Integer, Optional<T>> build,
            BiFunction<T, Service, String> render,
            Function<T, Object> summarise,
            String absent,
            Optional<Function<T, String>> markdown)
            implements Action {

        @Override
        public void run(Invocation call) {
            Service service = call.load();
            Optional<T> built = build.apply(service, call.valuation(0));
            if (built.isEmpty()) {
                call.print(absent, null);
                return;
            }
            T report = built.get();
            String text = call.markdown() && markdown.isPresent()
                    ? markdown.get().apply(report)
                    : render.apply(report, service);
            call.print(text, summarise.apply(report));
        }
    }

    static CommandSpec strip() {
        return Declare.command(
                "strip <package> <valuation>", "where each step of a package has got to", ValuationCommands::strip);
    }

    private static void strip(Invocation call) {
        Service service = call.load();
        int number = call.valuation(1);
        StripMap map = service.stripFor(call.argument(0), number);
        String heading = map.packageId() + ", " + service.period(number).describe();
        call.print(heading + "\n" + map.render(), map.packageId());
    }
}
