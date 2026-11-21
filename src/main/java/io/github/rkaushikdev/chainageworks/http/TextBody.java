package io.github.rkaushikdev.chainageworks.http;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The text an import is posted as: a {@code text/csv} or {@code text/plain} body, read as UTF-8.
 *
 * <p>A site export is the largest thing anybody posts, and a large one is eight thousand rows. {@value
 * #LIMIT} bytes leaves room for that and refuses the request that is actually somebody's photo of a
 * site diary, before any of it is read when the request says how long it is. A request with no body
 * at all is empty text. A body of any other type is refused, because there is nothing an import could
 * read out of it.
 */
final class TextBody {

    /** The most a body may hold, in bytes. */
    static final int LIMIT = 2 * 1024 * 1024;

    private TextBody() {}

    /**
     * The body of {@code request} as text.
     *
     * @throws RefusedException for a body over the limit or of a type that is not text
     */
    static String read(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > LIMIT) {
            throw RefusedException.tooLarge();
        }
        String type = request.getContentType();
        if (type == null) {
            if (request.getContentLengthLong() <= 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) == null) {
                return "";
            }
            throw RefusedException.unsupported("");
        }
        MediaType media = MediaType.parseMediaType(type);
        if (!media.getType().equals("text")
                || !(media.getSubtype().equals("csv") || media.getSubtype().equals("plain"))) {
            throw RefusedException.unsupported(type);
        }
        byte[] bytes = request.getInputStream().readNBytes(LIMIT + 1);
        if (bytes.length > LIMIT) {
            throw RefusedException.tooLarge();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** A request body the server will not read. */
    static final class RefusedException extends RuntimeException {

        private RefusedException(String message) {
            super(message);
        }

        static RefusedException tooLarge() {
            return new RefusedException("Request body is too large");
        }

        static RefusedException unsupported(String type) {
            return new RefusedException("Unsupported Media Type: " + type);
        }
    }
}
