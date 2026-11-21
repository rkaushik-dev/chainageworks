package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optional;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.orElse;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rangeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.defects.Attribution;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectInput;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectSeverity;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.Optional;
import java.util.function.Function;

/** The defects register. */
final class DefectCodec {

    private DefectCodec() {}

    private static <E> E oneOf(JsonNode value, String what, Function<String, Optional<E>> fromWire) {
        return wire(
                value,
                what,
                fromWire,
                text -> new BadInputException("not a " + what + " this tool knows", Details.of("got", text)));
    }

    static ObjectNode defectToJson(Defect defect) {
        ObjectNode json = Json.object();
        json.put("id", defect.id());
        json.put("reference", defect.reference());
        json.put("packageId", defect.packageId());
        json.set("extent", rangeToJson(defect.extent()));
        json.put("description", defect.description());
        json.put("severity", defect.severity().wire());
        json.put("attribution", defect.attribution().wire());
        json.put("raisedOn", dateToJson(defect.raisedOn()));
        json.put("dueBy", dateToJson(defect.dueBy()));
        json.set("rectifiedOn", optionalDateToJson(defect.rectifiedOn()));
        return json;
    }

    static Defect defectFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "defect");
        return Defect.build(new DefectInput(
                needString(raw.path("id"), "defect id"),
                optionalString(raw.path("reference")),
                needString(raw.path("packageId"), "package id"),
                optional(raw.path("extent"), ValueCodec::rangeFromJson).orElse(null),
                needString(raw.path("description"), "description"),
                oneOf(orElse(raw.path("severity"), "minor"), "defect severity", DefectSeverity::fromWire),
                oneOf(orElse(raw.path("attribution"), "contractor"), "defect attribution", Attribution::fromWire),
                dateFromJson(raw.path("raisedOn")),
                dateFromJson(raw.path("dueBy")),
                optionalDateFromJson(raw.path("rectifiedOn")).orElse(null)));
    }
}
