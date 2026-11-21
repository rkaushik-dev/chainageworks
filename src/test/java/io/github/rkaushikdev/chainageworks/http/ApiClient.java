package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP API over one service, answered in process with no socket, for the suites that drive it.
 *
 * <p>Built from the same parts {@link ChainageworksApplication} scans: the routes, the problem bodies,
 * the request ids, the job lock, the route guard and the writer every body goes through. A URL is sent exactly as
 * written, query and percent escapes included.
 */
public final class ApiClient {

    private final MockMvc mvc;

    private ApiClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** The API over {@code service}. */
    public static ApiClient over(Service service) {
        return new ApiClient(MockMvcBuilders.standaloneSetup(
                        new JobRoutes(service), new ReportRoutes(service), new ImportRoutes(service))
                .setControllerAdvice(new ApiErrors())
                .setMessageConverters(new JsonTreeConverter())
                .addInterceptors(new RouteGuard())
                .addFilters(new RequestIds(), new JobLock())
                .build());
    }

    /** The API over {@code job}, with the default config. */
    public static ApiClient over(Job job) {
        return over(new Service(job, Config.DEFAULT));
    }

    public Answer get(String url) {
        return send(HttpMethod.GET.name(), url);
    }

    /** Post {@code body} as {@code text/csv}. */
    public Answer postCsv(String url, String body) {
        return send(MockMvcRequestBuilders.request(HttpMethod.POST, URI.create(url))
                .contentType("text/csv")
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** Send {@code method} to {@code url} with no body. */
    public Answer send(String method, String url) {
        return send(MockMvcRequestBuilders.request(HttpMethod.valueOf(method), URI.create(url)));
    }

    /** Send a request built in full. */
    public Answer send(MockHttpServletRequestBuilder request) {
        try {
            return new Answer(mvc.perform(request).andReturn().getResponse());
        } catch (Exception failed) {
            throw new AssertionError("the request was not answered", failed);
        }
    }

    /**
     * What the server answered.
     *
     * @param response the response as it was written
     */
    public record Answer(MockHttpServletResponse response) {

        public int status() {
            return response.getStatus();
        }

        /** The body as text, empty when there is none. */
        public String body() {
            try {
                return response.getContentAsString(StandardCharsets.UTF_8);
            } catch (UnsupportedEncodingException impossible) {
                throw new AssertionError(impossible);
            }
        }

        /** The body read as JSON. */
        public JsonNode json() {
            try {
                return Json.parse(body());
            } catch (Json.JsonFailure broken) {
                throw new AssertionError("the body is not JSON: " + body(), broken);
            }
        }

        /** The header {@code name}, whatever case it is written in. */
        public Optional<String> header(String name) {
            return Optional.ofNullable(response.getHeader(name));
        }
    }
}
