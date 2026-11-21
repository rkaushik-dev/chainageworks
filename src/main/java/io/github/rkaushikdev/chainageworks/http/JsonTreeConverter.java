package io.github.rkaushikdev.chainageworks.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;

/**
 * Writes every response body, a JSON tree, with {@link Json#stringify(JsonNode)}.
 *
 * <p>Through the job's own writer rather than the framework's object mapper, so a body carries the
 * same number text and key order as the job file and the command line's output: {@code 3} rather
 * than {@code 3.0}, and fields in the order the shape lists them.
 *
 * <p>The body is JSON whatever the request's {@code Accept} header asks for. The API answers in one
 * format, and a client that asks for another one gets that format rather than a refusal.
 */
@Component
final class JsonTreeConverter extends AbstractHttpMessageConverter<JsonNode> {

    private static final MediaType JSON = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    JsonTreeConverter() {
        super(StandardCharsets.UTF_8, MediaType.APPLICATION_JSON, MediaType.ALL);
    }

    @Override
    protected boolean supports(Class<?> type) {
        return JsonNode.class.isAssignableFrom(type);
    }

    /** Bodies are read by the routes that take them, never by this converter. */
    @Override
    public boolean canRead(Class<?> type, MediaType mediaType) {
        return false;
    }

    @Override
    protected JsonNode readInternal(Class<? extends JsonNode> type, HttpInputMessage input) {
        throw new HttpMessageNotReadableException("request bodies are not read as JSON trees", input);
    }

    @Override
    protected void addDefaultHeaders(HttpHeaders headers, JsonNode body, MediaType requested) throws IOException {
        headers.setContentType(JSON);
        super.addDefaultHeaders(headers, body, JSON);
    }

    @Override
    protected void writeInternal(JsonNode body, HttpOutputMessage output) throws IOException {
        output.getBody().write(Json.stringify(body).getBytes(StandardCharsets.UTF_8));
    }
}
