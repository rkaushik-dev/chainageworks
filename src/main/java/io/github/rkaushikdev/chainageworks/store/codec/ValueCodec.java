package io.github.rkaushikdev.chainageworks.store.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * How the domain values are written to a file.
 *
 * <p>Chainage and dates go out as the strings a person would write, because the file is read by
 * people when a valuation is argued over and {@code 12+340.000} can be checked against a drawing while
 * {@code 12340500} cannot. Money and quantities go out as the integers they are held as, because those
 * have to come back exactly and a decimal string would invite a reader to round it.
 *
 * <p>The readers take any tree, because a file can hold anything: a field left out of its object
 * reads the same as one written as a JSON {@code null} wherever the field is optional.
 */
final class ValueCodec {

    private ValueCodec() {}

    /** Whether the value is absent: missing from its object or written as {@code null}. */
    static boolean isAbsent(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull();
    }

    /** The value, or {@code fallback} when it is absent. */
    static JsonNode orElse(JsonNode value, Object fallback) {
        return isAbsent(value) ? Json.valueOf(fallback) : value;
    }

    /** The value read by {@code read}, or nothing when it is absent. */
    static <T> Optional<T> optional(JsonNode value, Function<JsonNode, T> read) {
        return isAbsent(value) ? Optional.empty() : Optional.of(read.apply(value));
    }

    /** The JSON kind of a value, for an error message: {@code object}, {@code string}, {@code missing}... */
    static String kindOf(JsonNode value) {
        if (value == null || value.isMissingNode()) {
            return "missing";
        }
        return switch (value.getNodeType()) {
            case ARRAY -> "array";
            case OBJECT, POJO -> "object";
            case STRING -> "string";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case NULL -> "null";
            default -> "binary";
        };
    }

    /** The value as an error message quotes it: text as itself, numbers as written, absence as {@code missing}. */
    static String textOf(JsonNode value) {
        if (value == null || value.isMissingNode()) {
            return "missing";
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber()) {
            return Numbers.format(value.doubleValue());
        }
        return Json.stringify(value);
    }

    private static JsonNode need(JsonNode value, String what) {
        if (value == null || !value.isContainerNode()) {
            throw new BadInputException("expected an object for the " + what, Details.of("got", kindOf(value)));
        }
        return value;
    }

    private static double needNumber(JsonNode value, String what) {
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new BadInputException("expected a number for the " + what, Details.of("got", textOf(value)));
        }
        return value.doubleValue();
    }

    /**
     * A number read loosely, the way a hand-edited count is: a number as itself, numeric text by its
     * value ({@link Numbers#parse}), {@code true} and {@code false} as one and nought, {@code null} as
     * nought, and anything else, absence included, as {@code NaN} for the domain to refuse.
     */
    static double looseNumber(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isContainerNode()) {
            return Double.NaN;
        }
        if (value.isNull()) {
            return 0;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue() ? 1 : 0;
        }
        if (value.isTextual()) {
            return Numbers.parse(value.textValue());
        }
        return Double.NaN;
    }

    /**
     * A count of days, people or periods: read loosely, then refused unless it is a whole number.
     *
     * @throws BadInputException when it is not a whole number that fits a count
     */
    static int wholeNumber(JsonNode value, String what) {
        double number = looseNumber(value);
        if (!Numbers.isInteger(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new BadInputException("expected a whole number for the " + what, Details.of("got", textOf(value)));
        }
        return (int) number;
    }

    /** {@link #wholeNumber}, or {@code fallback} when the value is absent. */
    static int wholeNumber(JsonNode value, String what, int fallback) {
        return isAbsent(value) ? fallback : wholeNumber(value, what);
    }

    static String needString(JsonNode value, String what) {
        if (value == null || !value.isTextual()) {
            throw new BadInputException("expected a string for the " + what, Details.of("got", kindOf(value)));
        }
        return value.textValue();
    }

    /**
     * The constant an enum field names.
     *
     * @param refuse the refusal for text that names none, given the text
     */
    static <E> E wire(
            JsonNode value,
            String what,
            Function<String, Optional<E>> fromWire,
            Function<String, BadInputException> refuse) {
        String text = needString(value, what);
        return fromWire.apply(text).orElseThrow(() -> refuse.apply(text));
    }

    /** The written value, or {@code null} when there is none. */
    static JsonNode orNull(Optional<? extends JsonNode> value) {
        return value.isPresent() ? value.get() : NullNode.getInstance();
    }

    /** {@code null} when absent, the text otherwise. */
    static JsonNode text(Optional<String> value) {
        return value.<JsonNode>map(TextNode::valueOf).orElse(NullNode.getInstance());
    }

    static String stationToJson(long station) {
        return Stations.format(station);
    }

    static long stationFromJson(JsonNode value) {
        return Stations.parse(needString(value, "chainage"));
    }

    static ObjectNode rangeToJson(ChainageRange range) {
        ObjectNode json = Json.object();
        json.put("from", Stations.format(range.from()));
        json.put("to", Stations.format(range.to()));
        return json;
    }

    /** {@code null} when there is no range, the range otherwise. */
    static JsonNode rangeToJson(Optional<ChainageRange> range) {
        return range.<JsonNode>map(ValueCodec::rangeToJson).orElse(NullNode.getInstance());
    }

    static ChainageRange rangeFromJson(JsonNode value) {
        JsonNode raw = need(value, "chainage range");
        return ChainageRange.of(stationFromJson(raw.path("from")), stationFromJson(raw.path("to")));
    }

    static String dateToJson(LocalDate date) {
        return SiteDates.format(date);
    }

    static LocalDate dateFromJson(JsonNode value) {
        return SiteDates.parse(needString(value, "date"));
    }

    static JsonNode optionalDateToJson(Optional<LocalDate> date) {
        return date.<JsonNode>map(day -> TextNode.valueOf(SiteDates.format(day)))
                .orElse(NullNode.getInstance());
    }

    static Optional<LocalDate> optionalDateFromJson(JsonNode value) {
        return optional(value, ValueCodec::dateFromJson);
    }

    static ObjectNode moneyToJson(Money value) {
        ObjectNode json = Json.object();
        json.put("minor", value.minor());
        json.put("currency", value.currency().code());
        return json;
    }

    /** {@code null} when there is no amount, the amount otherwise. */
    static JsonNode moneyToJson(Optional<Money> value) {
        return value.<JsonNode>map(ValueCodec::moneyToJson).orElse(NullNode.getInstance());
    }

    static Money moneyFromJson(JsonNode value) {
        JsonNode raw = need(value, "money");
        Currency currency = currencyFromJson(raw.path("currency"));
        return Money.of(needNumber(raw.path("minor"), "money"), currency);
    }

    static Currency currencyFromJson(JsonNode value) {
        return wire(
                value,
                "currency",
                Currency::fromCode,
                code -> new BadInputException("unknown currency", Details.of("currency", code)));
    }

    static Unit unitFromJson(JsonNode value) {
        return wire(
                value, "unit", Unit::fromCode, code -> new BadInputException("unknown unit", Details.of("unit", code)));
    }

    static ObjectNode quantityToJson(Quantity value) {
        ObjectNode json = Json.object();
        json.put("milli", value.milli());
        json.put("unit", value.unit().code());
        return json;
    }

    static Quantity quantityFromJson(JsonNode value) {
        JsonNode raw = need(value, "quantity");
        double milli = needNumber(raw.path("milli"), "quantity");
        return Quantity.ofMilli(milli, unitFromJson(raw.path("unit")));
    }

    static ObjectNode rateToJson(Rate value) {
        ObjectNode json = Json.object();
        json.put("milliMinor", value.milliMinor());
        json.put("currency", value.currency().code());
        json.put("unit", value.unit().code());
        return json;
    }

    /** {@code null} when there is no rate, the rate otherwise. */
    static JsonNode rateToJson(Optional<Rate> value) {
        return value.<JsonNode>map(ValueCodec::rateToJson).orElse(NullNode.getInstance());
    }

    static Rate rateFromJson(JsonNode value) {
        JsonNode raw = need(value, "rate");
        double milliMinor = needNumber(raw.path("milliMinor"), "rate");
        Currency currency = currencyFromJson(raw.path("currency"));
        return Rate.ofMilliMinor(milliMinor, currency, unitFromJson(raw.path("unit")));
    }

    static long percentToJson(Percent value) {
        return value.basisPoints();
    }

    /** {@code null} when there is no percentage, its basis points otherwise. */
    static JsonNode percentToJson(Optional<Percent> value) {
        return value.<JsonNode>map(p -> Json.valueOf(p.basisPoints())).orElse(NullNode.getInstance());
    }

    static Percent percentFromJson(JsonNode value) {
        return Percent.ofBasisPoints(needNumber(value, "percentage"));
    }

    static String codeToJson(ItemCode code) {
        return code.format();
    }

    static ItemCode codeFromJson(JsonNode value) {
        return ItemCode.parse(needString(value, "item code"));
    }

    /** The value as an object to read fields from; a list passes too, and reads every field as absent. */
    static JsonNode objectFromJson(JsonNode value, String what) {
        return need(value, what);
    }

    static List<JsonNode> arrayFromJson(JsonNode value, String what) {
        if (value == null || !value.isArray()) {
            throw new BadInputException("expected a list for the " + what, Details.of("got", kindOf(value)));
        }
        List<JsonNode> entries = new ArrayList<>(value.size());
        value.forEach(entries::add);
        return entries;
    }

    /** The entries of a list, each read by {@code read}. */
    static <T> List<T> listFromJson(JsonNode value, String what, Function<JsonNode, T> read) {
        return arrayFromJson(value, what).stream().map(read).toList();
    }

    /** The items written one by one into a list. */
    static <T> ArrayNode listToJson(List<T> items, Function<T, ? extends JsonNode> write) {
        ArrayNode json = Json.array();
        items.forEach(item -> json.add(write.apply(item)));
        return json;
    }

    /** The texts written into a list. */
    static ArrayNode textsToJson(List<String> items) {
        ArrayNode json = Json.array();
        items.forEach(json::add);
        return json;
    }

    static String optionalString(JsonNode value) {
        return optionalString(value, "");
    }

    static String optionalString(JsonNode value, String fallback) {
        return isAbsent(value) ? fallback : needString(value, "text");
    }

    static boolean booleanFromJson(JsonNode value, String what) {
        if (value == null || !value.isBoolean()) {
            throw new BadInputException("expected true or false for the " + what, Details.of("got", kindOf(value)));
        }
        return value.booleanValue();
    }
}
