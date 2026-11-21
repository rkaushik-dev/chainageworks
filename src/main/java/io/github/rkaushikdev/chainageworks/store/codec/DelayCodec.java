package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optional;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.orElse;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rangeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.textsToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayCause;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEventInput;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.List;
import java.util.Optional;

/** The completion terms on the job header and the delay event register. */
final class DelayCodec {

    private DelayCodec() {}

    private static DelayCause causeFromJson(JsonNode value) {
        return wire(
                value,
                "delay cause",
                DelayCause::fromWire,
                text -> new BadInputException("not a delay cause this tool knows", Details.of("cause", text)));
    }

    static ObjectNode completionToJson(CompletionTerms terms) {
        ObjectNode json = Json.object();
        json.put("baseCompletion", dateToJson(terms.baseCompletion()));
        json.set("damagesPerDay", moneyToJson(terms.damagesPerDay()));
        json.set("damagesCap", percentToJson(terms.damagesCap()));
        json.put("noticeDays", terms.noticeDays());
        return json;
    }

    /** The terms, or none when the header carries {@code null} or nothing. */
    static Optional<CompletionTerms> completionFromJson(JsonNode value) {
        return optional(value, DelayCodec::termsFromJson);
    }

    private static CompletionTerms termsFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "completion terms");
        return CompletionTerms.build(
                dateFromJson(raw.path("baseCompletion")),
                moneyFromJson(raw.path("damagesPerDay")),
                optional(raw.path("damagesCap"), ValueCodec::percentFromJson).orElse(null),
                wholeNumber(raw.path("noticeDays"), "notice days"));
    }

    static ObjectNode delayEventToJson(DelayEvent event) {
        ObjectNode json = Json.object();
        json.put("id", event.id());
        json.put("reference", event.reference());
        json.put("cause", event.cause().wire());
        json.put("description", event.description());
        json.put("from", dateToJson(event.period().from()));
        json.put("to", dateToJson(event.period().to()));
        json.set("extent", rangeToJson(event.extent()));
        json.set("packageIds", textsToJson(event.packageIds()));
        json.set("notifiedOn", optionalDateToJson(event.notifiedOn()));
        json.set("awardedDays", event.awardedDays().<JsonNode>map(Json::valueOf).orElse(NullNode.getInstance()));
        return json;
    }

    static DelayEvent delayEventFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "delay event");
        return DelayEvent.build(new DelayEventInput(
                needString(raw.path("id"), "event id"),
                optionalString(raw.path("reference")),
                causeFromJson(raw.path("cause")),
                needString(raw.path("description"), "description"),
                dateFromJson(raw.path("from")),
                dateFromJson(raw.path("to")),
                optional(raw.path("extent"), ValueCodec::rangeFromJson).orElse(null),
                listFromJson(
                        orElse(raw.path("packageIds"), List.of()), "package ids", id -> needString(id, "package id")),
                optionalDateFromJson(raw.path("notifiedOn")).orElse(null),
                optional(raw.path("awardedDays"), days -> wholeNumber(days, "awarded days"))
                        .orElse(null)));
    }
}
