package io.github.rkaushikdev.chainageworks.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * JSON documents: the job file, the API's bodies and the {@code --json} output of the command line
 * are all trees of {@link JsonNode}, written by one writer.
 *
 * <p>The writer's output is fixed so that a file written today diffs cleanly against one written
 * last month (RFC 8259 text): keys in insertion order, numbers as {@link Numbers#format(double)}
 * writes them (so {@code 3.0} is {@code 3}), non-finite numbers as {@code null}, absent values left
 * out, strings escaped only where the grammar requires it, and an indented layout of two spaces per
 * level with {@code "key": value} pairs and empty containers written {@code []} and {@code {}}.
 */
public final class Json {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private Json() {}

    /** A new, empty object. */
    public static ObjectNode object() {
        return NODES.objectNode();
    }

    /** A new, empty array. */
    public static ArrayNode array() {
        return NODES.arrayNode();
    }

    /** The shared reader, for code that binds a tree onto a type. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * A number value holding {@code value}: a whole number becomes an integer value, so it is written
     * and compared as one.
     */
    public static JsonNode number(double value) {
        if (Double.isFinite(value) && Math.floor(value) == value && Math.abs(value) < 0x1p53) {
            return NODES.numberNode((long) value);
        }
        return NODES.numberNode(value);
    }

    /**
     * The tree for a plain Java value: maps become objects, iterables arrays, numbers, strings and
     * booleans themselves, {@code null} a JSON null, an empty {@code Optional} a missing value.
     */
    public static JsonNode valueOf(Object value) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof JsonNode tree) {
            return tree;
        }
        if (value instanceof String text) {
            return NODES.textNode(text);
        }
        if (value instanceof Boolean flag) {
            return NODES.booleanNode(flag);
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return NODES.numberNode(((Number) value).longValue());
        }
        if (value instanceof Number number) {
            return number(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode object = object();
            map.forEach((key, entry) -> object.set(String.valueOf(key), valueOf(entry)));
            return object;
        }
        if (value instanceof Iterable<?> items) {
            ArrayNode array = array();
            items.forEach(item -> array.add(valueOf(item)));
            return array;
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(Json::valueOf).orElse(MissingNode.getInstance());
        }
        if (value instanceof OptionalInt optional) {
            return optional.isPresent() ? NODES.numberNode((long) optional.getAsInt()) : MissingNode.getInstance();
        }
        if (value instanceof OptionalLong optional) {
            return optional.isPresent() ? NODES.numberNode(optional.getAsLong()) : MissingNode.getInstance();
        }
        if (value instanceof OptionalDouble optional) {
            return optional.isPresent() ? number(optional.getAsDouble()) : MissingNode.getInstance();
        }
        if (value instanceof Enum<?> || value instanceof CharSequence) {
            return NODES.textNode(value.toString());
        }
        throw new IllegalArgumentException(
                "no JSON form for " + value.getClass().getName());
    }

    /** Read JSON text into a tree. */
    public static JsonNode parse(String text) throws JsonFailure {
        try {
            JsonNode tree = MAPPER.readTree(text);
            if (tree == null || tree.isMissingNode()) {
                throw new JsonFailure("Unexpected end of JSON input");
            }
            return tree;
        } catch (JsonProcessingException broken) {
            throw new JsonFailure(broken.getOriginalMessage());
        }
    }

    /** The tree written on one line, with no spaces. */
    public static String stringify(JsonNode value) {
        return stringify(value, 0);
    }

    /** The tree written with {@code indent} spaces per level, or on one line when it is zero. */
    public static String stringify(JsonNode value, int indent) {
        StringBuilder out = new StringBuilder();
        String step = " ".repeat(Math.max(0, Math.min(indent, 10)));
        if (!write(out, value, step, "")) {
            return "";
        }
        return out.toString();
    }

    /** The text of one string literal, quotes included. */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        writeString(out, value);
        return out.toString();
    }

    private static boolean write(StringBuilder out, JsonNode value, String step, String indent) {
        if (value == null || value.isMissingNode()) {
            return false;
        }
        if (value.isNull()) {
            out.append("null");
        } else if (value.isBoolean()) {
            out.append(value.booleanValue());
        } else if (value.isTextual()) {
            writeString(out, value.textValue());
        } else if (value.isIntegralNumber() && value.canConvertToLong()) {
            long whole = value.longValue();
            out.append(Math.abs(whole) < 0x1p53 ? Long.toString(whole) : finite(value.doubleValue()));
        } else if (value.isNumber()) {
            out.append(finite(value.doubleValue()));
        } else if (value.isArray()) {
            writeArray(out, value, step, indent);
        } else if (value.isObject()) {
            writeObject(out, value, step, indent);
        } else {
            out.append("null");
        }
        return true;
    }

    private static String finite(double number) {
        return Double.isFinite(number) ? Numbers.format(number) : "null";
    }

    private static void writeArray(StringBuilder out, JsonNode array, String step, String indent) {
        if (array.isEmpty()) {
            out.append("[]");
            return;
        }
        String inner = indent + step;
        out.append('[');
        boolean first = true;
        for (JsonNode item : array) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (!step.isEmpty()) {
                out.append('\n').append(inner);
            }
            if (!write(out, item, step, inner)) {
                out.append("null");
            }
        }
        if (!step.isEmpty()) {
            out.append('\n').append(indent);
        }
        out.append(']');
    }

    private static void writeObject(StringBuilder out, JsonNode object, String step, String indent) {
        String inner = indent + step;
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (field.getValue() == null || field.getValue().isMissingNode()) {
                continue;
            }
            if (!first) {
                body.append(',');
            }
            first = false;
            if (!step.isEmpty()) {
                body.append('\n').append(inner);
            }
            writeString(body, field.getKey());
            body.append(step.isEmpty() ? ":" : ": ");
            write(body, field.getValue(), step, inner);
        }
        if (first) {
            out.append("{}");
            return;
        }
        out.append('{').append(body);
        if (!step.isEmpty()) {
            out.append('\n').append(indent);
        }
        out.append('}');
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || isLoneSurrogate(value, i)) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static boolean isLoneSurrogate(String value, int i) {
        char c = value.charAt(i);
        if (Character.isHighSurrogate(c)) {
            return i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1));
        }
        if (Character.isLowSurrogate(c)) {
            return i == 0 || !Character.isHighSurrogate(value.charAt(i - 1));
        }
        return false;
    }

    /** JSON text that could not be read. */
    public static final class JsonFailure extends Exception {

        JsonFailure(String message) {
            super(message);
        }
    }
}
