package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.reporting.CurvePoint;
import io.github.rkaushikdev.chainageworks.reporting.HaulReport;
import io.github.rkaushikdev.chainageworks.reporting.LookaheadReport;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.valuation.CashPoint;
import java.util.List;
import picocli.CommandLine.Model.CommandSpec;

/** The forward looking commands: the lookahead, the haul plan, the cash forecast and the curve. */
final class PlanCommands {

    private static final String WEEKS = "--weeks";
    private static final String GANGS = "--gangs";
    private static final String CHASE = "--chase";
    private static final String MUCK_AWAY = "--muck-away";
    private static final String PERIODS = "--periods";
    private static final String LATE = "--late";

    private PlanCommands() {}

    /** The planning commands, in the order the tree lists them. */
    static List<CommandSpec> all() {
        return List.of(lookahead(), haul(), cash(), curve());
    }

    static CommandSpec lookahead() {
        return Declare.command(
                "lookahead <from>",
                "what the next few weeks have to get through",
                PlanCommands::lookahead,
                Declare.option("-w, --weeks <count>", "how many weeks, default from the config"),
                Declare.option("-g, --gangs <count>", "gangs for each step, default from the config"),
                Declare.option("--chase", "print only the inspections holding work up"));
    }

    private static void lookahead(Invocation call) {
        Service service = call.load();
        Config.Reports defaults = service.config().reports();
        Lookahead plan = service.lookaheadFrom(
                SiteDates.parse(call.argument(0)),
                call.count(WEEKS, defaults.lookaheadWeeks()),
                call.count(GANGS, defaults.gangs()));
        if (call.flag(CHASE)) {
            List<String> chase = LookaheadReport.chaseInspections(plan);
            call.print(String.join("\n", chase), chase);
            return;
        }
        call.print(LookaheadReport.render(plan), plan.steps().size());
    }

    static CommandSpec haul() {
        return Declare.command(
                "haul",
                "where the material has to go and what moving it costs",
                PlanCommands::haul,
                Declare.option("--muck-away", "print only what has to leave each source"));
    }

    private static void haul(Invocation call) {
        Service service = call.load();
        HaulAllocation plan = service.haulPlan();
        List<HaulSite> sites = service.job().haulSites().all();
        if (call.flag(MUCK_AWAY)) {
            List<String> schedule = HaulReport.muckAwaySchedule(plan, sites);
            call.print(String.join("\n", schedule), schedule);
            return;
        }
        call.print(HaulReport.render(plan, sites), plan.movements().size());
    }

    static CommandSpec cash() {
        return Declare.command(
                "cash",
                "what the certificates will bring in and when",
                PlanCommands::cash,
                Declare.option("-n, --periods <count>", "how many periods, default from the config"),
                Declare.option("--late <periods>", "periods the payer runs behind", "0"));
    }

    private static void cash(Invocation call) {
        Service service = call.load();
        List<CashPoint> points = service.cashFlowFor(
                call.count(PERIODS, service.config().reports().periods()),
                latePeriods(call.option(LATE).orElseThrow()));
        call.print(PlainText.cashFlow(points), points.size());
    }

    /**
     * The late flag as a number of periods. A negative number is handed on, for the forecast to refuse
     * in the same words.
     *
     * @throws BadInputException unless it reads as a whole number
     */
    private static int latePeriods(String raw) {
        double value = Numbers.parse(raw);
        if (!Numbers.isInteger(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new BadInputException("late payment is a whole number of periods", Details.of("late", value));
        }
        return (int) value;
    }

    static CommandSpec curve() {
        return Declare.command(
                "curve",
                "planned against earned, period by period",
                PlanCommands::curve,
                Declare.option("-n, --periods <count>", "how many periods, default from the config"));
    }

    private static void curve(Invocation call) {
        Service service = call.load();
        List<CurvePoint> points =
                service.curveFor(call.count(PERIODS, service.config().reports().periods()));
        call.print(PlainText.curve(points), points.size());
    }
}
