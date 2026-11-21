package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.app.Diagnosis;
import io.github.rkaushikdev.chainageworks.app.Doctor;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The job itself: what it holds, its schedule, its bill, its packages and its survey records.
 *
 * <p>Everything that reads takes an as-at date or a count, and nothing defaults to today. A report that
 * quietly means "now" cannot be compared against the one somebody printed last week, and comparing
 * those two is most of what this API is used for.
 */
@RestController
final class JobRoutes {

    /** The period whose cut-off the records are read at when no date is given: as late as anybody values. */
    private static final int FAR_OUT = 999;

    private final Service service;

    JobRoutes(Service service) {
        this.service = service;
    }

    @GetMapping("/health")
    JsonNode health() {
        ObjectNode body = Json.object();
        body.put("status", "ok");
        return body;
    }

    /** Every route this server answers, which is the only documentation that cannot go stale. */
    @GetMapping("/routes")
    JsonNode routes(HttpServletRequest request) {
        ObjectNode body = Json.object();
        body.set("routes", Dto.array(RouteTable.of(request).entries(), entry -> {
            ObjectNode route = Json.object();
            route.put("path", entry.path());
            route.set("methods", Dto.strings(entry.methods()));
            return route;
        }));
        return body;
    }

    @GetMapping("/job")
    JsonNode job() {
        Job job = service.job();
        ObjectNode body = Json.object();
        body.put("id", job.settings().id());
        body.put("name", job.settings().name());
        body.put("currency", job.settings().currency().code());
        body.set("contractSum", Dto.money(job.settings().contractSum()));
        ObjectNode counts = Json.object();
        counts.put("alignments", job.alignments().count());
        counts.put("billRevisions", job.billRevisions().count());
        counts.put("packages", job.packages().count());
        counts.put("plans", job.plans().count());
        counts.put("signOffs", job.signOffs().count());
        counts.put("ncrs", job.ncrs().count());
        counts.put("records", job.records().count());
        counts.put("materials", job.materials().count());
        counts.put("variations", job.variations().count());
        counts.put("dayworkSheets", job.dayworkSheets().count());
        counts.put("delayEvents", job.delayEvents().count());
        counts.put("defects", job.defects().count());
        counts.put("subcontracts", job.subcontracts().count());
        counts.put("paymentApplications", job.paymentApplications().count());
        counts.put("resourceReturns", job.resourceReturns().count());
        counts.put("indices", job.indices().count());
        counts.put("haulSites", job.haulSites().count());
        counts.put("earlyWarnings", job.earlyWarnings().count());
        body.set("counts", counts);
        body.set("blockers", Dto.strings(job.blockers()));
        return body;
    }

    @GetMapping("/schedule")
    JsonNode schedule(HttpServletRequest request) {
        int periods =
                Params.count(request, "periods", service.config().reports().periods());
        return Dto.array(service.schedule(periods).periods(), Dto::period);
    }

    /** The bill in force at {@code asAt}, the first cut-off when no date is given. */
    @GetMapping("/bill")
    JsonNode bill(HttpServletRequest request) {
        ProgressContext context = service.context();
        LocalDate asAt = Params.date(request, "asAt", service.period(1).cutOff());
        Bill bill = context.billHistory().billAsAt(asAt);
        ObjectNode body = Json.object();
        body.put("id", bill.id());
        body.put("title", bill.title());
        body.put("currency", bill.currency().code());
        body.set("items", Dto.array(bill.items(), item -> {
            ObjectNode entry = Json.object();
            entry.put("code", item.code().format());
            entry.put("description", item.description());
            entry.put("unit", item.unit().code());
            entry.set("rate", Json.number(item.rate().major()));
            entry.put("quantity", item.quantity().format());
            entry.put("kind", item.kind().wire());
            return entry;
        }));
        return body;
    }

    @GetMapping("/packages")
    JsonNode packages() {
        return Dto.array(service.job().packages().all(), pkg -> {
            ObjectNode entry = Json.object();
            entry.put("id", pkg.id());
            entry.put("name", pkg.name());
            entry.put("section", pkg.section());
            entry.put("alignmentId", pkg.alignmentId());
            entry.put("extent", pkg.extent().format());
            entry.put("profileId", pkg.profileId());
            entry.set("allocations", allocations(pkg));
            return entry;
        });
    }

    @GetMapping("/packages/{id}")
    JsonNode onePackage(@PathVariable String id) {
        WorkPackage pkg = service.job().packages().require(id);
        ObjectNode body = Json.object();
        body.put("id", pkg.id());
        body.put("name", pkg.name());
        body.put("section", pkg.section());
        body.put("extent", pkg.extent().format());
        body.put("profileId", pkg.profileId());
        body.set("allocations", allocations(pkg));
        return body;
    }

    private static JsonNode allocations(WorkPackage pkg) {
        return Dto.array(pkg.allocations(), allocation -> {
            ObjectNode entry = Json.object();
            entry.put("code", allocation.code().format());
            entry.put("quantity", allocation.quantity().format());
            return entry;
        });
    }

    /**
     * The survey records that count at {@code asAt}, the latest cut-off the schedule reaches when no
     * date is given, for one package when {@code package} names one.
     */
    @GetMapping("/records")
    JsonNode records(HttpServletRequest request) {
        LocalDate asAt = Params.date(request, "asAt", service.period(FAR_OUT).cutOff());
        String[] wanted = request.getParameterValues("package");
        List<SurveyRecord> records = SurveyRecord.effective(
                        service.job().records().all(), asAt)
                .stream()
                .filter(record -> wanted == null || (wanted.length == 1 && wanted[0].equals(record.packageId())))
                .toList();
        return Dto.array(records, record -> {
            ObjectNode entry = Json.object();
            entry.put("id", record.id());
            entry.put("packageId", record.packageId());
            entry.put("code", record.code().format());
            entry.put("stepId", record.stepId());
            entry.put("extent", record.extent().format());
            entry.put("quantity", record.quantity().format());
            entry.put("measuredOn", Dto.date(record.measuredOn()));
            entry.put("bookedBy", record.bookedBy());
            entry.put("method", record.method().wire());
            return entry;
        });
    }

    /**
     * The job's health, answered 200 with the findings rather than 500 on a job with problems.
     *
     * <p>A monitor wants to know the server is up; a person wants to know the job is sound, and
     * conflating the two makes the endpoint useless for both.
     */
    @GetMapping("/doctor")
    JsonNode doctor() {
        Diagnosis diagnosis = Doctor.diagnose(service.job());
        ObjectNode body = Json.object();
        body.put("healthy", diagnosis.isHealthy());
        body.put("errors", diagnosis.errors());
        body.put("warnings", diagnosis.warnings());
        body.set("blockers", Dto.strings(diagnosis.blockers()));
        body.set("findings", Dto.array(diagnosis.findings(), finding -> {
            ObjectNode entry = Json.object();
            entry.put("level", finding.level().wire());
            entry.put("register", finding.register());
            entry.put("subject", finding.subject());
            entry.put("message", finding.message());
            return entry;
        }));
        return body;
    }
}
