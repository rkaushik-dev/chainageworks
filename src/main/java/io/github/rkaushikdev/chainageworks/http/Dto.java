package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.Movement;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentFactor;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.PriceAdjustment;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister.ReconciliationLine;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet.OverMeasure;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.ingest.ImportIssue;
import io.github.rkaushikdev.chainageworks.ingest.ImportReport;
import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.PackageOutput;
import io.github.rkaushikdev.chainageworks.progress.StepLookahead;
import io.github.rkaushikdev.chainageworks.reporting.CurvePoint;
import io.github.rkaushikdev.chainageworks.reporting.DefectsReport;
import io.github.rkaushikdev.chainageworks.reporting.DelayReport;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport.PackageLine;
import io.github.rkaushikdev.chainageworks.reporting.RevisionReport;
import io.github.rkaushikdev.chainageworks.reporting.StripMap;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport;
import io.github.rkaushikdev.chainageworks.reporting.VariationReport;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.valuation.BondPosition;
import io.github.rkaushikdev.chainageworks.valuation.CashFlow;
import io.github.rkaushikdev.chainageworks.valuation.CashPoint;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.FinalAccount;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import io.github.rkaushikdev.chainageworks.valuation.RetentionState;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * How the reports go over the wire.
 *
 * <p>Every shape is a JSON tree built field by field, so the field names and their order are the API's
 * and not whatever a record happens to declare. Dates are written {@code 2025-03-25}, chainage ranges
 * {@code 12+000.000 to 12+200.000}, item codes as the bill prints them and choices by their wire
 * names. A value the report does not have is written {@code null} rather than left out, so a client
 * can tell "none" from a field it has never heard of.
 */
public final class Dto {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Dto() {}

    /**
     * How money goes over the wire.
     *
     * <p>Three fields rather than one, because the three readers want different things. A spreadsheet
     * wants the number, a person wants the formatted string, and anything doing arithmetic wants the
     * exact minor units. Sending only the formatted string is how a client ends up parsing currency
     * symbols.
     */
    public static ObjectNode money(Money value) {
        ObjectNode dto = Json.object();
        dto.put("minor", value.minor());
        dto.set("amount", Json.number(value.major()));
        dto.put("formatted", value.format());
        return dto;
    }

    /** A quantity with its unit: the exact thousandths, the amount, the unit code and the printed form. */
    public static ObjectNode quantity(Quantity value) {
        ObjectNode dto = Json.object();
        dto.put("milli", value.milli());
        dto.set("amount", Json.number(value.amount()));
        dto.put("unit", value.unit().code());
        dto.put("formatted", value.format());
        return dto;
    }

    /** A percentage as a plain number: {@code 32.5} for 32.5%. */
    public static double percent(Percent value) {
        return value.value();
    }

    /** Every date on a valuation period. */
    public static ObjectNode period(ValuationPeriod period) {
        ObjectNode dto = Json.object();
        dto.put("number", period.number());
        dto.put("from", date(period.range().from()));
        dto.put("to", date(period.range().to()));
        dto.put("cutOff", date(period.cutOff()));
        dto.put("issuedOn", date(period.issuedOn()));
        dto.put("dueOn", date(period.dueOn()));
        return dto;
    }

    /** One package of a progress report, with the detail of every step. */
    public static ObjectNode packageLine(PackageLine line) {
        ObjectNode dto = Json.object();
        dto.put("packageId", line.packageId());
        dto.put("name", line.name());
        dto.put("section", line.section());
        dto.put("extent", line.extent().format());
        dto.set("budget", money(line.budget()));
        dto.set("earned", money(line.earned()));
        dto.set("percent", percentNode(line.fraction()));
        dto.set("heldValue", money(line.heldValue()));
        dto.set("steps", array(line.steps(), step -> {
            ObjectNode entry = Json.object();
            entry.put("stepId", step.stepId());
            entry.set("builtPercent", fixed(step.builtFraction() * 100, 2));
            entry.set("creditedPercent", fixed(step.creditedFraction() * 100, 2));
            entry.put("gate", step.gate().wire());
            entry.set("heldPoints", strings(step.heldPoints()));
            entry.set("heldBack", ranges(step.heldBack()));
            return entry;
        }));
        return dto;
    }

    /** The progress report for a period: the headline figures, every package, and what does not add up. */
    public static ObjectNode progressReport(ProgressReport report) {
        ObjectNode dto = Json.object();
        dto.set("period", period(report.period()));
        dto.put("billRevision", report.billRevision());
        dto.set("budget", money(report.budget()));
        dto.set("earned", money(report.earned()));
        dto.set("heldValue", money(report.heldValue()));
        dto.put("workingDays", report.workingDays());
        dto.put("productiveDays", report.productiveDays());
        dto.set("standdowns", array(report.standdowns(), Dto::standdown));
        dto.set("packages", array(report.lines(), Dto::packageLine));
        dto.set("reconciliation", array(report.reconciliation(), Dto::reconciliationLine));
        dto.set("overMeasures", array(report.overMeasures(), Dto::overMeasure));
        return dto;
    }

    private static ObjectNode standdown(Standdown standdown) {
        ObjectNode dto = Json.object();
        dto.put("date", date(standdown.date()));
        dto.put("reason", standdown.reason().wire());
        dto.put("note", standdown.note().orElse(""));
        return dto;
    }

    private static ObjectNode reconciliationLine(ReconciliationLine line) {
        ObjectNode dto = Json.object();
        dto.put("code", line.code().format());
        dto.put("problem", line.problem().wire());
        dto.set("billed", orNull(line.billed(), Dto::quantity));
        dto.set("allocated", orNull(line.allocated(), Dto::quantity));
        dto.put("message", line.message());
        return dto;
    }

    private static ObjectNode overMeasure(OverMeasure over) {
        ObjectNode dto = Json.object();
        dto.put("packageId", over.packageId());
        dto.put("code", over.code().format());
        dto.set("measured", quantity(over.measured()));
        dto.set("allowed", quantity(over.allowed()));
        dto.set("excess", quantity(over.excess()));
        dto.put("message", over.message());
        return dto;
    }

    /** An interim certificate walked from the measured work down to the amount due, with its backing sheet. */
    public static ObjectNode certificate(Certificate certificate) {
        ObjectNode dto = Json.object();
        dto.set("period", period(certificate.period()));
        dto.put("billRevision", certificate.billRevision());
        dto.set("lines", array(certificate.lines(), line -> {
            ObjectNode entry = Json.object();
            entry.put("name", line.name());
            entry.set("amount", money(line.amount()));
            entry.put("total", line.total());
            return entry;
        }));
        dto.set("notes", strings(certificate.notes()));
        dto.set("measuredWork", money(certificate.measuredWork()));
        dto.set("materialsOnSite", money(certificate.materialsOnSite()));
        dto.set("daywork", money(certificate.daywork()));
        dto.set("priceAdjustment", priceAdjustment(certificate.priceAdjustment()));
        dto.set("gross", money(certificate.gross()));
        dto.set("retention", retention(certificate.retention()));
        dto.set("bond", orNull(certificate.bond(), Dto::bond));
        dto.set("retentionHeld", money(certificate.retentionHeld()));
        dto.set("damages", money(certificate.damages()));
        dto.set("netValuation", money(certificate.netValuation()));
        dto.set("previouslyCertified", money(certificate.previouslyCertified()));
        dto.set("amountDue", money(certificate.amountDue()));
        dto.set("packages", array(certificate.earnings(), Dto::earning));
        return dto;
    }

    private static ObjectNode priceAdjustment(PriceAdjustment adjustment) {
        AdjustmentFactor factor = adjustment.factor();
        ObjectNode dto = Json.object();
        dto.set("factor", Json.number(factor.factor()));
        dto.set("adjustable", money(adjustment.adjustable()));
        dto.set("amount", money(adjustment.amount()));
        dto.put("provisional", adjustment.provisional());
        dto.put("indicesReadAt", date(factor.indicesReadAt()));
        dto.put("heldAtCompletion", adjustment.heldAtCompletion());
        dto.set("indices", array(factor.lines(), line -> {
            ObjectNode entry = Json.object();
            entry.put("indexId", line.indexId());
            entry.set("weight", percentNode(line.weight()));
            entry.set("movement", Json.number(line.movement()));
            entry.put("provisional", line.provisional());
            return entry;
        }));
        return dto;
    }

    private static ObjectNode retention(RetentionState retention) {
        ObjectNode dto = Json.object();
        dto.set("accrued", money(retention.accrued()));
        dto.set("held", money(retention.held()));
        dto.set("releasedOnCompletion", money(retention.releasedOnCompletion()));
        dto.set("releasedOnDefects", money(retention.releasedOnDefects()));
        dto.put("capReached", retention.capReached());
        return dto;
    }

    private static ObjectNode bond(BondPosition position) {
        ObjectNode dto = Json.object();
        dto.put("id", position.bond().id());
        dto.put("surety", position.bond().surety());
        dto.set("cover", money(position.cover()));
        dto.set("released", money(position.released()));
        dto.set("shortfall", money(position.shortfall()));
        dto.put("expired", position.expired());
        dto.put("daysToExpiry", position.daysToExpiry());
        return dto;
    }

    private static ObjectNode earning(PackageEarning earning) {
        ObjectNode dto = Json.object();
        dto.put("packageId", earning.packageId());
        dto.set("percent", percentNode(earning.fraction()));
        dto.set("budget", money(earning.budget()));
        dto.set("earned", money(earning.earnedValue()));
        dto.set("allocations", array(earning.allocations(), allocation -> {
            ObjectNode entry = Json.object();
            entry.put("code", allocation.code().format());
            entry.set("allocated", quantity(allocation.allocated()));
            entry.set("measured", quantity(allocation.measured()));
            entry.set("earned", quantity(allocation.earnedQuantity()));
            entry.set("value", money(allocation.earnedValue()));
            entry.put("cappedByMeasurement", allocation.cappedByMeasurement());
            entry.put("notEarnable", allocation.notEarnable());
            return entry;
        }));
        return dto;
    }

    /** Planned against earned, one row per point of the curve. */
    public static ArrayNode curve(List<CurvePoint> points) {
        return array(points, point -> {
            ObjectNode dto = Json.object();
            dto.put("period", point.period());
            dto.put("cutOff", date(point.cutOff()));
            dto.set("planned", money(point.planned()));
            dto.set("earned", money(point.earned()));
            dto.set("variance", money(point.variance()));
            dto.set("plannedPercent", percentNode(point.plannedShare()));
            dto.set("earnedPercent", percentNode(point.earnedShare()));
            return dto;
        });
    }

    /** A strip map, each row both as the line of glyphs a person reads and as the cells a program reads. */
    public static ObjectNode strip(StripMap map) {
        ObjectNode dto = Json.object();
        dto.put("packageId", map.packageId());
        dto.put("extent", map.extent().format());
        dto.put("cellLength", map.cellLength());
        dto.set("rows", array(map.rows(), row -> {
            ObjectNode entry = Json.object();
            entry.put("stepId", row.stepId());
            entry.put("glyphs", row.glyphs());
            entry.set("cells", array(row.cells(), cell -> {
                ObjectNode item = Json.object();
                item.put("extent", cell.extent().format());
                item.put("state", cell.state().wire());
                return item;
            }));
            return entry;
        }));
        return dto;
    }

    /** What an import read, took and left out, with every issue it raised. */
    public static ObjectNode importReport(ImportReport<?> report) {
        ObjectNode dto = Json.object();
        dto.put("rowsRead", report.rowsRead());
        dto.put("accepted", report.rowsAccepted());
        dto.put("rejected", report.rowsRejected());
        dto.set("issues", array(report.issues(), Dto::issue));
        return dto;
    }

    private static ObjectNode issue(ImportIssue issue) {
        ObjectNode dto = Json.object();
        dto.set("line", orNull(issue.line(), line -> NODES.numberNode(line.longValue())));
        dto.set("column", orNull(issue.column(), NODES::textNode));
        dto.put("severity", issue.severity().wire());
        dto.put("message", issue.message());
        return dto;
    }

    /**
     * The delay position.
     *
     * <p>The barred days ride alongside the awarded days rather than being folded into one figure. A
     * client that only sees the award cannot tell a job that was never delayed from one that was delayed
     * and served its notices late, and those two need completely different conversations.
     */
    public static ObjectNode delayReport(DelayReport report) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(report.asAt()));
        dto.put("baseCompletion", date(report.position().baseCompletion()));
        dto.put("extendedCompletion", date(report.position().extendedCompletion()));
        dto.put("awardedDays", report.position().awardedDays());
        dto.put("barredDays", report.position().barredDays());
        dto.put("concurrentDays", report.position().concurrentDays());
        dto.put("claimedDays", report.claimedDays());
        dto.put("daysLate", report.daysLate());
        dto.set("damages", money(report.damages()));
        dto.put("damagesCapped", report.damagesCapped());
        dto.set(
                "barred",
                strings(report.barredLines().stream()
                        .map(DelayReport.DelayLine::id)
                        .toList()));
        dto.set(
                "unassessed",
                strings(report.unassessedLines().stream()
                        .map(DelayReport.DelayLine::id)
                        .toList()));
        dto.set("events", array(report.lines(), line -> {
            ObjectNode entry = Json.object();
            entry.put("id", line.id());
            entry.put("reference", line.reference());
            entry.put("cause", line.cause().wire());
            entry.put("entitlement", line.entitlement().wire());
            entry.put("from", date(line.from()));
            entry.put("to", date(line.to()));
            entry.put("workingDays", line.workingDays());
            entry.set("awardedDays", orNull(line.awardedDays(), days -> NODES.numberNode(days.longValue())));
            entry.set("bar", orNull(line.bar(), bar -> NODES.textNode(bar.wire())));
            entry.put("carriesMoney", line.carriesMoney());
            return entry;
        }));
        return dto;
    }

    /** The defects list at a date, and what is stopping the defects certificate. */
    public static ObjectNode defectsReport(DefectsReport report) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(report.asAt()));
        dto.put("periodEnds", date(report.periodEnds()));
        dto.set("block", orNull(report.block(), block -> NODES.textNode(block.wire())));
        ObjectNode summary = Json.object();
        summary.put("outstanding", report.summary().outstanding());
        summary.put("overdue", report.summary().overdue());
        summary.put("disputed", report.summary().disputed());
        ObjectNode bySeverity = Json.object();
        report.summary().bySeverity().forEach((severity, count) -> bySeverity.put(severity.wire(), count));
        summary.set("bySeverity", bySeverity);
        dto.set("summary", summary);
        dto.set("outstanding", array(report.outstanding(), defect -> {
            ObjectNode entry = Json.object();
            entry.put("id", defect.id());
            entry.put("reference", defect.reference());
            entry.put("packageId", defect.packageId());
            entry.set("extent", orNull(defect.extent(), Dto::range));
            entry.put("description", defect.description());
            entry.put("severity", defect.severity().wire());
            entry.put("attribution", defect.attribution().wire());
            entry.put("raisedOn", date(defect.raisedOn()));
            entry.put("dueBy", date(defect.dueBy()));
            return entry;
        }));
        dto.set("overdue", strings(report.overdue().stream().map(Defect::id).toList()));
        return dto;
    }

    /** Every subcontract order, what it has been paid and set off, and the notice diary. */
    public static ObjectNode subcontractReport(SubcontractReport report) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(report.asAt()));
        dto.put("exposure", report.exposure());
        dto.set(
                "overOrder",
                strings(report.overOrder().stream()
                        .map(position -> position.subcontract().id())
                        .toList()));
        dto.set("barredContra", array(report.barredContra(), barred -> {
            ObjectNode entry = Json.object();
            entry.put("subcontractId", barred.subcontractId());
            entry.put("amount", barred.amount());
            return entry;
        }));
        dto.set("orders", array(report.positions(), Dto::order));
        dto.set("diary", array(report.diary(), entry -> {
            ObjectNode item = Json.object();
            item.put("applicationId", entry.applicationId());
            item.put("subcontractId", entry.subcontractId());
            item.put("contractor", entry.contractor());
            item.put("appliedOn", date(entry.appliedOn()));
            item.put("noticeBy", date(entry.noticeBy()));
            item.put("amount", entry.amount());
            item.put("missed", entry.missed());
            return item;
        }));
        return dto;
    }

    private static ObjectNode order(Subcontract.Position position) {
        ObjectNode dto = Json.object();
        dto.put("id", position.subcontract().id());
        dto.put("contractor", position.subcontract().contractor());
        dto.set("orderValue", money(position.subcontract().orderValue()));
        dto.put("applications", position.applications());
        dto.set("certified", money(position.certifiedToDate()));
        dto.set("paid", money(position.paidToDate()));
        dto.set("remaining", money(position.remaining()));
        dto.set("defaulted", strings(position.defaulted()));
        dto.set("contra", contra(position.contra()));
        dto.set("netOfContra", money(position.netOfContra()));
        dto.set("carriedForward", money(position.contraCarriedForward()));
        return dto;
    }

    private static ObjectNode contra(ContraCharge.Position contra) {
        ObjectNode dto = Json.object();
        dto.set("raised", money(contra.raised()));
        dto.set("deductible", money(contra.deductible()));
        dto.set("disputed", money(contra.disputed()));
        dto.set("barred", money(contra.barred()));
        dto.set("charges", array(contra.charges(), charge -> {
            ObjectNode entry = Json.object();
            entry.put("id", charge.id());
            entry.put("reference", charge.reference());
            entry.put("reason", charge.reason().wire());
            entry.set("amount", money(charge.amount()));
            entry.put("disputed", charge.disputed());
            return entry;
        }));
        return dto;
    }

    /** What the job produced for the hours it booked, package by package and step by step. */
    public static ObjectNode outputReport(OutputReport report) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(report.asAt()));
        ObjectNode totals = Json.object();
        totals.set("labourHours", Json.number(report.totals().labourHours()));
        totals.set("plantHours", Json.number(report.totals().plantHours()));
        totals.set("workingHours", Json.number(report.totals().workingHours()));
        totals.set("standingHours", Json.number(report.totals().standingHours()));
        totals.set("idleHours", Json.number(report.totals().idleHours()));
        totals.put("returns", report.totals().returns());
        totals.put("days", report.totals().days());
        dto.set("totals", totals);
        dto.set("lostFraction", fixed(report.lostFraction(), 4));
        dto.set("unallocated", array(report.unallocatedHours(), entry -> {
            ObjectNode item = Json.object();
            item.put("packageId", entry.packageId());
            item.set("hours", Json.number(entry.hours()));
            return item;
        }));
        dto.set("behind", array(report.behind(), entry -> {
            ObjectNode item = Json.object();
            item.put("packageId", entry.packageId());
            item.put("stepId", entry.step().stepId());
            item.set("ratio", orNull(entry.step().ratio()));
            return item;
        }));
        dto.set("barren", array(report.barren(), entry -> {
            ObjectNode item = Json.object();
            item.put("packageId", entry.packageId());
            item.put("stepId", entry.step().stepId());
            item.set("labourHours", Json.number(entry.step().labourHours()));
            return item;
        }));
        dto.set("packages", array(report.packages(), Dto::packageOutput));
        return dto;
    }

    private static ObjectNode packageOutput(PackageOutput output) {
        ObjectNode dto = Json.object();
        dto.put("packageId", output.packageId());
        dto.set("labourHours", Json.number(output.labourHours()));
        dto.set("plantHours", Json.number(output.plantHours()));
        dto.set("unallocatedHours", Json.number(output.unallocatedHours()));
        dto.set("steps", array(output.steps(), step -> {
            ObjectNode entry = Json.object();
            entry.put("stepId", step.stepId());
            entry.set("builtMetres", Json.number(step.builtMetres()));
            entry.set("labourHours", Json.number(step.labourHours()));
            entry.set("plantHours", Json.number(step.plantHours()));
            entry.set("lostHours", Json.number(step.lostHours()));
            entry.set("achieved", orNull(step.achieved()));
            entry.set("allowed", orNull(step.allowed()));
            entry.set("ratio", orNull(step.ratio()));
            return entry;
        }));
        return dto;
    }

    /** The cash forecast, with the period the job is owed most at. */
    public static ObjectNode cashFlow(List<CashPoint> points) {
        ObjectNode dto = Json.object();
        dto.set("peak", orNull(CashFlow.peakExposure(points), peak -> {
            ObjectNode entry = Json.object();
            entry.put("period", peak.period());
            entry.put("dueOn", date(peak.dueOn()));
            entry.set("outstanding", money(peak.outstanding()));
            return entry;
        }));
        dto.set("points", array(points, point -> {
            ObjectNode entry = Json.object();
            entry.put("period", point.period());
            entry.put("cutOff", date(point.cutOff()));
            entry.put("dueOn", date(point.dueOn()));
            entry.set("certified", money(point.certified()));
            entry.set("certificate", money(point.certificate()));
            entry.set("receipt", money(point.receipt()));
            entry.set("received", money(point.received()));
            entry.set("outstanding", money(point.outstanding()));
            return entry;
        }));
        return dto;
    }

    /** The lookahead window, the steps that can start now and the ones waiting on an inspection. */
    public static ObjectNode lookahead(Lookahead lookahead) {
        ObjectNode dto = Json.object();
        dto.put("from", date(lookahead.from()));
        dto.put("to", date(lookahead.to()));
        dto.put("workingDays", lookahead.workingDays());
        dto.set("daysOfWork", fixed(lookahead.daysOfWork(), 3));
        dto.put("fitsInWindow", lookahead.fitsInWindow());
        dto.set(
                "ready",
                strings(lookahead.readyNow().stream().map(Dto::stepName).toList()));
        dto.set(
                "heldForInspection",
                strings(lookahead.waitingOnInspection().stream()
                        .map(Dto::stepName)
                        .toList()));
        dto.set("steps", array(lookahead.steps(), step -> {
            ObjectNode entry = Json.object();
            entry.put("packageId", step.packageId());
            entry.put("stepId", step.stepId());
            entry.set("remaining", ranges(step.remaining()));
            entry.set("remainingMetres", Json.number(step.remainingMetres()));
            entry.set("days", orNull(step.days()));
            entry.set("waitingOn", strings(step.waitingOn()));
            entry.set("heldPoints", strings(step.heldPoints()));
            return entry;
        }));
        return dto;
    }

    private static String stepName(StepLookahead step) {
        return step.packageId() + "/" + step.stepId();
    }

    /** The haul plan: what it costs, whether it balances, and every movement in it. */
    public static ObjectNode haulPlan(HaulAllocation allocation) {
        ObjectNode dto = Json.object();
        dto.set("cost", money(allocation.cost()));
        dto.set("haulMoment", fixed(allocation.haulMoment(), 3));
        dto.put("balanced", allocation.isBalanced());
        dto.set("unplaced", quantity(allocation.unplaced()));
        dto.set("unfilled", quantity(allocation.unfilled()));
        dto.set("spareTipCapacity", quantity(allocation.spareTipCapacity()));
        dto.set("movements", array(allocation.movements(), Dto::movement));
        return dto;
    }

    private static ObjectNode movement(Movement movement) {
        ObjectNode dto = Json.object();
        dto.put("fromId", movement.fromId());
        dto.put("toId", movement.toId());
        dto.set("volume", quantity(movement.volume()));
        dto.set("km", Json.number(movement.km()));
        dto.set("haulCost", money(movement.haulCost()));
        dto.set("siteCost", money(movement.siteCost()));
        dto.set("cost", money(movement.cost()));
        return dto;
    }

    /** The anticipated final account: the parts it adds up from and every line of the bill. */
    public static ObjectNode outturn(Outturn outturn) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(outturn.asAt()));
        dto.set("contractSum", money(outturn.contractSum()));
        dto.set("parts", array(outturn.parts(), part -> {
            ObjectNode entry = Json.object();
            entry.put("name", part.name());
            entry.set("amount", money(part.amount()));
            return entry;
        }));
        dto.set("total", money(outturn.total()));
        dto.set("movement", money(outturn.movement()));
        dto.set("movementPercent", percentNode(outturn.movementPercent()));
        dto.put("overContractSum", outturn.isOverContractSum());
        dto.put("provisionalAdjustment", outturn.priceAdjustment().provisional());
        dto.set("lines", array(outturn.account().lines(), Dto::finalAccountLine));
        return dto;
    }

    private static ObjectNode finalAccountLine(FinalAccount.Line line) {
        ObjectNode dto = Json.object();
        dto.put("code", line.code().format());
        dto.put("description", line.description());
        dto.set("billed", money(line.billed()));
        dto.set("measured", money(line.measured()));
        dto.set("forecast", money(line.forecast()));
        dto.set("movement", money(line.movement()));
        dto.set("percentComplete", Json.number(line.percentComplete()));
        dto.put("extrapolated", line.extrapolated());
        return dto;
    }

    /** The variation register: what is in force, what is agreed, and what needs chasing. */
    public static ObjectNode variationReport(VariationReport report) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(report.asAt()));
        dto.set("inForce", money(report.inForce()));
        dto.set("unagreed", money(report.unagreed()));
        dto.put("proposed", report.proposed());
        dto.set("chase", strings(report.chaseAgreement()));
        dto.set("touchedItems", strings(report.touchedItems()));
        dto.set("orders", array(report.lines(), line -> {
            ObjectNode entry = Json.object();
            entry.put("id", line.variation().id());
            entry.put("reference", line.variation().reference());
            entry.put("title", line.variation().title());
            entry.put("status", line.variation().status().wire());
            entry.set("extent", orNull(line.variation().extent(), Dto::range));
            entry.set(
                    "bases",
                    strings(line.bases().stream().map(ValuationBasis::wire).toList()));
            entry.put("inForce", line.inForce());
            entry.put("agreed", line.agreed());
            entry.set("value", money(line.value()));
            return entry;
        }));
        return dto;
    }

    /** The early warning register: what the open risks are expected to cost, and who is chasing them. */
    public static ObjectNode riskPosition(EarlyWarning.Position position) {
        ObjectNode dto = Json.object();
        dto.put("asAt", date(position.asAt()));
        dto.set("expected", money(position.expected()));
        dto.set("worstCase", money(position.worstCase()));
        dto.set("carried", money(position.carried()));
        dto.put("delayDays", position.delayDays());
        dto.set(
                "overdueMeetings",
                strings(position.overdueMeetings().stream()
                        .map(EarlyWarning::id)
                        .toList()));
        dto.set(
                "unowned",
                strings(position.unowned().stream().map(EarlyWarning::id).toList()));
        dto.set("warnings", array(position.open(), warning -> {
            ObjectNode entry = Json.object();
            entry.put("id", warning.id());
            entry.put("reference", warning.reference());
            entry.put("description", warning.description());
            entry.put("owner", warning.owner().wire());
            entry.put("state", warning.state().wire());
            entry.set("extent", orNull(warning.extent(), Dto::range));
            entry.put("raisedOn", date(warning.raisedOn()));
            entry.set("meetingOn", orNull(warning.meetingOn(), on -> NODES.textNode(date(on))));
            entry.set("costIfItHappens", money(warning.costIfItHappens()));
            entry.set("likelihood", percentNode(warning.likelihood()));
            entry.set("expected", money(warning.expectedCost()));
            entry.put("delayDays", warning.delayDays());
            return entry;
        }));
        return dto;
    }

    /** Every revision of the bill against the one before it, and the rates that moved. */
    public static ObjectNode revisionReport(RevisionReport report) {
        ObjectNode dto = Json.object();
        dto.set("rateChanges", strings(report.rateChanges()));
        dto.set("steps", array(report.steps(), step -> {
            ObjectNode entry = Json.object();
            entry.put("from", step.from().reference());
            entry.put("to", step.to().reference());
            entry.put("effectiveFrom", date(step.to().effectiveFrom()));
            entry.set("netMovement", money(step.diff().netDelta()));
            entry.set("changes", array(step.diff().changes(), change -> {
                ObjectNode item = Json.object();
                item.put("code", change.code().format());
                item.put("kind", change.kind().wire());
                item.set("delta", money(change.delta()));
                return item;
            }));
            return entry;
        }));
        return dto;
    }

    static String date(LocalDate date) {
        return SiteDates.format(date);
    }

    static ArrayNode strings(List<String> values) {
        ArrayNode array = Json.array();
        values.forEach(array::add);
        return array;
    }

    static <T> ArrayNode array(List<T> items, Function<? super T, ? extends JsonNode> dto) {
        ArrayNode array = Json.array();
        items.forEach(item -> array.add(dto.apply(item)));
        return array;
    }

    private static JsonNode range(ChainageRange range) {
        return NODES.textNode(range.format());
    }

    private static ArrayNode ranges(List<ChainageRange> ranges) {
        return strings(ranges.stream().map(ChainageRange::format).toList());
    }

    private static JsonNode percentNode(Percent value) {
        return Json.number(percent(value));
    }

    /** {@code value} rounded to {@code places} decimals, half up, as a number rather than as text. */
    private static JsonNode fixed(double value, int places) {
        return Json.number(Numbers.parse(Numbers.toFixed(value, places)));
    }

    private static <T> JsonNode orNull(Optional<T> value, Function<? super T, ? extends JsonNode> dto) {
        return value.<JsonNode>map(dto).orElse(NODES.nullNode());
    }

    private static JsonNode orNull(OptionalDouble value) {
        return value.isPresent() ? Json.number(value.getAsDouble()) : NODES.nullNode();
    }
}
