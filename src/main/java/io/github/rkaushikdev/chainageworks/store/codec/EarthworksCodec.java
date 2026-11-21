package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.looseNumber;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.quantityFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.quantityToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.util.Json;

/**
 * The haul sites register and the haul links on the job header.
 *
 * <p>Haul links are settings rather than records, so they ride on the job header. A distance between
 * two sites is a fact about the ground, not about the works, and it does not change when a lorry runs.
 * Keeping it out of the repositories means an import of movements cannot silently rewrite it.
 */
final class EarthworksCodec {

    private EarthworksCodec() {}

    private static SiteKind kindFromJson(JsonNode value) {
        return wire(
                value,
                "haul site kind",
                SiteKind::fromWire,
                text -> new BadInputException("not a kind of haul site this tool knows", Details.of("got", text)));
    }

    static ObjectNode haulSiteToJson(HaulSite site) {
        ObjectNode json = Json.object();
        json.put("id", site.id());
        json.put("name", site.name());
        json.put("kind", site.kind().wire());
        json.set("volume", quantityToJson(site.volume()));
        json.set("unitCost", moneyToJson(site.unitCost()));
        return json;
    }

    static HaulSite haulSiteFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "haul site");
        return new HaulSite(
                needString(raw.path("id"), "site id"),
                optionalString(raw.path("name")),
                kindFromJson(raw.path("kind")),
                quantityFromJson(raw.path("volume")),
                moneyFromJson(raw.path("unitCost")));
    }

    static ObjectNode haulLinkToJson(HaulLink link) {
        ObjectNode json = Json.object();
        json.put("fromId", link.fromId());
        json.put("toId", link.toId());
        json.set("km", Json.number(link.km()));
        return json;
    }

    /**
     * A haul link, refusing a distance that is negative or not a number.
     *
     * @throws BadInputException for a bad distance
     */
    static HaulLink haulLinkFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "haul link");
        double km = looseNumber(raw.path("km"));
        if (!Double.isFinite(km) || km < 0) {
            throw new BadInputException("a haul distance cannot be negative", Details.of("km", km));
        }
        return new HaulLink(needString(raw.path("fromId"), "from site"), needString(raw.path("toId"), "to site"), km);
    }
}
