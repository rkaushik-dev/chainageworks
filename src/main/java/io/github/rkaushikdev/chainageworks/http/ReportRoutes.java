package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reports: every report on a valuation, and the forward looking ones keyed on a date or a count.
 *
 * <p>Every report on a valuation hangs off the same path and answers the same two ways. A report the
 * contract has no term for comes back 204 rather than 404: there is nothing wrong with the request and
 * nothing wrong with the job, so a 404 would send a client looking for a routing mistake that is not
 * there.
 */
@RestController
final class ReportRoutes {

    private final Service service;

    ReportRoutes(Service service) {
        this.service = service;
    }

    @GetMapping("/valuations/{number}/progress")
    JsonNode progress(@PathVariable String number) {
        return Dto.progressReport(service.progressFor(valuation(number)));
    }

    @GetMapping("/valuations/{number}/certificate")
    JsonNode certificate(@PathVariable String number) {
        return Dto.certificate(service.certificateFor(valuation(number)));
    }

    /** The extension of time position, or 204 when the contract fixes no completion date. */
    @GetMapping("/valuations/{number}/delay")
    ResponseEntity<JsonNode> delay(@PathVariable String number) {
        return whenTheContractHasTheTerm(service.delayFor(valuation(number)), Dto::delayReport);
    }

    /** The defects position, or 204 when the contract fixes no defects period. */
    @GetMapping("/valuations/{number}/defects")
    ResponseEntity<JsonNode> defects(@PathVariable String number) {
        return whenTheContractHasTheTerm(service.defectsFor(valuation(number)), Dto::defectsReport);
    }

    @GetMapping("/valuations/{number}/payments")
    JsonNode payments(@PathVariable String number) {
        return Dto.subcontractReport(service.paymentsFor(valuation(number)));
    }

    @GetMapping("/valuations/{number}/output")
    JsonNode output(@PathVariable String number) {
        return Dto.outputReport(service.outputFor(valuation(number)));
    }

    @GetMapping("/valuations/{number}/outturn")
    JsonNode outturn(@PathVariable String number) {
        return Dto.outturn(service.outturnFor(valuation(number)));
    }

    @GetMapping("/valuations/{number}/variations")
    JsonNode variations(@PathVariable String number) {
        return Dto.variationReport(service.variationsFor(valuation(number)));
    }

    @GetMapping("/valuations/{number}/risks")
    JsonNode risks(@PathVariable String number) {
        return Dto.riskPosition(service.risksFor(valuation(number)));
    }

    @GetMapping("/valuations/{number}/strip/{packageId}")
    JsonNode strip(@PathVariable String number, @PathVariable String packageId) {
        int valuation = valuation(number);
        return Dto.strip(service.stripFor(packageId, valuation));
    }

    /**
     * The lookahead from {@code from}, over {@code weeks} weeks with {@code gangs} gangs to a step.
     *
     * <p>The one report keyed on a date rather than a valuation, and it defaults to the first cut-off
     * rather than to today. Nothing this server answers means "now", so that two people running the same
     * query on different days get the same answer. The window and the gangs default to the config.
     */
    @GetMapping("/lookahead")
    JsonNode lookahead(HttpServletRequest request) {
        Config.Reports defaults = service.config().reports();
        LocalDate from = Params.date(request, "from", service.period(1).cutOff());
        int weeks = Params.count(request, "weeks", defaults.lookaheadWeeks());
        int gangs = Params.count(request, "gangs", defaults.gangs());
        return Dto.lookahead(service.lookaheadFrom(from, weeks, gangs));
    }

    @GetMapping("/haul")
    JsonNode haul() {
        return Dto.haulPlan(service.haulPlan());
    }

    @GetMapping("/revisions")
    JsonNode revisions() {
        return Dto.revisionReport(service.revisionsFor());
    }

    /**
     * The cash forecast over {@code periods} periods with every certificate paid {@code late} periods
     * after it falls due.
     *
     * <p>The late query is a what-if, not a record of anything. It is on the report rather than on the
     * job because the question is always asked twice, once against the terms and once against what the
     * payer actually does.
     */
    @GetMapping("/cash")
    JsonNode cash(HttpServletRequest request) {
        int periods =
                Params.count(request, "periods", service.config().reports().periods());
        int late = Params.fromZero(request, "late");
        return Dto.cashFlow(service.cashFlowFor(periods, late));
    }

    @GetMapping("/curve")
    JsonNode curve(HttpServletRequest request) {
        int periods =
                Params.count(request, "periods", service.config().reports().periods());
        return Dto.curve(service.curveFor(periods));
    }

    private static int valuation(String number) {
        return Params.fromOne(number, "valuation");
    }

    private static <T> ResponseEntity<JsonNode> whenTheContractHasTheTerm(
            Optional<T> report, Function<? super T, ? extends JsonNode> dto) {
        return report.<ResponseEntity<JsonNode>>map(found -> ResponseEntity.ok(dto.apply(found)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
