package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.looseNumber;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optional;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.orElse;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.text;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturnInput;
import io.github.rkaushikdev.chainageworks.domain.resource.Shift;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.List;

/** The resource returns register and the output allowances on the job header. */
final class ResourceCodec {

    private ResourceCodec() {}

    private static HourKind hourKindFromJson(JsonNode value) {
        return wire(
                orElse(value, HourKind.WORKING.wire()),
                "hour kind",
                HourKind::fromWire,
                text -> new BadInputException(
                        "not a way of spending an hour this tool knows", Details.of("got", text)));
    }

    private static ObjectNode lineToJson(ResourceLine line) {
        ObjectNode json = Json.object();
        json.put("resourceId", line.resourceId());
        json.put("description", line.description());
        json.put("count", line.count());
        json.set("hours", Json.number(line.hours()));
        json.put("kind", line.kind().wire());
        return json;
    }

    private static ResourceLine lineFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "resource line");
        return new ResourceLine(
                needString(raw.path("resourceId"), "resource id"),
                optionalString(raw.path("description")),
                wholeNumber(raw.path("count"), "count"),
                looseNumber(raw.path("hours")),
                hourKindFromJson(raw.path("kind")));
    }

    static ObjectNode resourceReturnToJson(ResourceReturn entry) {
        ObjectNode json = Json.object();
        json.put("id", entry.id());
        json.put("packageId", entry.packageId());
        json.set("stepId", text(entry.stepId()));
        json.put("date", dateToJson(entry.date()));
        json.put("shift", entry.shift().wire());
        json.set("labour", listToJson(entry.labour(), ResourceCodec::lineToJson));
        json.set("plant", listToJson(entry.plant(), ResourceCodec::lineToJson));
        json.put("returnedBy", entry.returnedBy());
        json.put("note", entry.note());
        return json;
    }

    /** A return; any shift but {@code night} reads as a day shift. */
    static ResourceReturn resourceReturnFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "resource return");
        JsonNode shift = raw.path("shift");
        return ResourceReturn.build(new ResourceReturnInput(
                needString(raw.path("id"), "return id"),
                needString(raw.path("packageId"), "package id"),
                optional(raw.path("stepId"), step -> needString(step, "step id"))
                        .orElse(null),
                dateFromJson(raw.path("date")),
                shift.isTextual() && shift.textValue().equals(Shift.NIGHT.wire()) ? Shift.NIGHT : Shift.DAY,
                listFromJson(orElse(raw.path("labour"), List.of()), "labour lines", ResourceCodec::lineFromJson),
                listFromJson(orElse(raw.path("plant"), List.of()), "plant lines", ResourceCodec::lineFromJson),
                needString(raw.path("returnedBy"), "returned by"),
                optionalString(raw.path("note"))));
    }

    static ObjectNode allowanceToJson(OutputAllowance allowance) {
        ObjectNode json = Json.object();
        json.put("stepId", allowance.stepId());
        json.set("metresPerHour", Json.number(allowance.metresPerHour()));
        return json;
    }

    static OutputAllowance allowanceFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "output allowance");
        return new OutputAllowance(needString(raw.path("stepId"), "step id"), looseNumber(raw.path("metresPerHour")));
    }
}
