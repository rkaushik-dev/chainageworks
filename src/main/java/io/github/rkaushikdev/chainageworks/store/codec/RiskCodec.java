package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optional;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.orElse;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rangeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarningInput;
import io.github.rkaushikdev.chainageworks.domain.risk.RiskOwner;
import io.github.rkaushikdev.chainageworks.domain.risk.RiskState;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.Optional;
import java.util.function.Function;

/** The early warning register. */
final class RiskCodec {

    private RiskCodec() {}

    private static <E> E oneOf(JsonNode value, String what, Function<String, Optional<E>> fromWire) {
        return wire(
                value,
                what,
                fromWire,
                text -> new BadInputException("not a " + what + " this tool knows", Details.of("got", text)));
    }

    static ObjectNode earlyWarningToJson(EarlyWarning warning) {
        ObjectNode json = Json.object();
        json.put("id", warning.id());
        json.put("reference", warning.reference());
        json.put("description", warning.description());
        json.put("owner", warning.owner().wire());
        json.put("state", warning.state().wire());
        json.set("extent", rangeToJson(warning.extent()));
        json.put("raisedOn", dateToJson(warning.raisedOn()));
        json.set("meetingOn", optionalDateToJson(warning.meetingOn()));
        json.set("closedOn", optionalDateToJson(warning.closedOn()));
        json.set("costIfItHappens", moneyToJson(warning.costIfItHappens()));
        json.put("likelihood", percentToJson(warning.likelihood()));
        json.put("delayDays", warning.delayDays());
        return json;
    }

    static EarlyWarning earlyWarningFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "early warning");
        return EarlyWarning.build(new EarlyWarningInput(
                needString(raw.path("id"), "warning id"),
                optionalString(raw.path("reference")),
                needString(raw.path("description"), "description"),
                oneOf(orElse(raw.path("owner"), "unresolved"), "risk owner", RiskOwner::fromWire),
                oneOf(orElse(raw.path("state"), "open"), "risk state", RiskState::fromWire),
                optional(raw.path("extent"), ValueCodec::rangeFromJson).orElse(null),
                dateFromJson(raw.path("raisedOn")),
                optionalDateFromJson(raw.path("meetingOn")).orElse(null),
                optionalDateFromJson(raw.path("closedOn")).orElse(null),
                moneyFromJson(raw.path("costIfItHappens")),
                percentFromJson(raw.path("likelihood")),
                wholeNumber(raw.path("delayDays"), "delay days", 0)));
    }
}
