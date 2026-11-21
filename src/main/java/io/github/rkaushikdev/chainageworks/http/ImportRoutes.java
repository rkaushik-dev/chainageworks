package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.ingest.ImportReport;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The csv imports, one route per register, each posted as {@code text/csv} or {@code text/plain}.
 *
 * <p>An import applies nothing when any row is wrong unless {@code ?partial=true} asks for the clean
 * rows anyway. It answers 207 when it left rows out. A flat 200 would tell a client that uploaded four
 * hundred rows and had two rejected that everything went in.
 */
@RestController
final class ImportRoutes {

    private final Service service;

    ImportRoutes(Service service) {
        this.service = service;
    }

    /** One of the service's loads: the text of a sheet in, its report out. */
    @FunctionalInterface
    private interface Load {
        ImportReport<?> apply(String text, boolean partial);
    }

    @PostMapping("/import/records")
    ResponseEntity<JsonNode> records(HttpServletRequest request) throws IOException {
        return load(request, service::loadRecords);
    }

    @PostMapping("/import/sign-offs")
    ResponseEntity<JsonNode> signOffs(HttpServletRequest request) throws IOException {
        return load(request, service::loadSignOffs);
    }

    @PostMapping("/import/ncrs")
    ResponseEntity<JsonNode> ncrs(HttpServletRequest request) throws IOException {
        return load(request, service::loadNcrs);
    }

    @PostMapping("/import/diary")
    ResponseEntity<JsonNode> diary(HttpServletRequest request) throws IOException {
        return load(request, service::loadDiary);
    }

    @PostMapping("/import/applications")
    ResponseEntity<JsonNode> applications(HttpServletRequest request) throws IOException {
        return load(request, service::loadApplications);
    }

    @PostMapping("/import/notices")
    ResponseEntity<JsonNode> notices(HttpServletRequest request) throws IOException {
        return load(request, service::loadNotices);
    }

    @PostMapping("/import/returns")
    ResponseEntity<JsonNode> returns(HttpServletRequest request) throws IOException {
        return load(request, service::loadResourceReturns);
    }

    @PostMapping("/import/indices")
    ResponseEntity<JsonNode> indices(HttpServletRequest request) throws IOException {
        return load(request, service::loadIndices);
    }

    @PostMapping("/import/pay-less")
    ResponseEntity<JsonNode> payLess(HttpServletRequest request) throws IOException {
        return load(request, service::loadPayLessNotices);
    }

    private static ResponseEntity<JsonNode> load(HttpServletRequest request, Load load) throws IOException {
        String text = TextBody.read(request);
        ImportReport<?> report = load.apply(text, Params.flag(request, "partial"));
        HttpStatus status = report.rowsRejected() == 0 ? HttpStatus.OK : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status).body(Dto.importReport(report));
    }
}
