package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optional;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.valuation.RetentionBond;
import io.github.rkaushikdev.chainageworks.valuation.RetentionBondInput;
import java.util.Optional;

/** The retention bond on the job header. */
final class BondCodec {

    private BondCodec() {}

    static ObjectNode retentionBondToJson(RetentionBond bond) {
        ObjectNode json = Json.object();
        json.put("id", bond.id());
        json.put("surety", bond.surety());
        json.set("maximum", moneyToJson(bond.maximum()));
        json.put("issuedOn", dateToJson(bond.issuedOn()));
        json.put("expiresOn", dateToJson(bond.expiresOn()));
        json.put("rate", percentToJson(bond.rate()));
        json.set("reducesTo", moneyToJson(bond.reducesTo()));
        return json;
    }

    /** The bond, or none when the header carries {@code null} or nothing. */
    static Optional<RetentionBond> retentionBondFromJson(JsonNode value) {
        return optional(value, BondCodec::bondFromJson);
    }

    private static RetentionBond bondFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "retention bond");
        return RetentionBond.build(new RetentionBondInput(
                needString(raw.path("id"), "bond id"),
                needString(raw.path("surety"), "surety"),
                moneyFromJson(raw.path("maximum")),
                dateFromJson(raw.path("issuedOn")),
                dateFromJson(raw.path("expiresOn")),
                percentFromJson(raw.path("rate")),
                optional(raw.path("reducesTo"), ValueCodec::moneyFromJson).orElse(null)));
    }
}
