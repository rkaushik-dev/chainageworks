package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.booleanFromJson;
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
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexReading;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.List;
import java.util.Optional;

/** The price adjustment clause on the job header and the index series register. */
final class FluctuationCodec {

    private FluctuationCodec() {}

    static ObjectNode indexSeriesToJson(IndexSeries series) {
        ObjectNode json = Json.object();
        json.put("id", series.id());
        json.put("name", series.name());
        json.set("readings", listToJson(series.readings(), reading -> {
            ObjectNode entry = Json.object();
            entry.put("year", reading.year());
            entry.put("month", reading.month());
            entry.set("value", Json.number(reading.value()));
            entry.put("provisional", reading.provisional());
            return entry;
        }));
        return json;
    }

    static IndexSeries indexSeriesFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "index series");
        return IndexSeries.build(
                needString(raw.path("id"), "index id"),
                optionalString(raw.path("name")),
                listFromJson(orElse(raw.path("readings"), List.of()), "index readings", entry -> {
                    JsonNode reading = objectFromJson(entry, "index reading");
                    return new IndexReading(
                            wholeNumber(reading.path("year"), "index year"),
                            wholeNumber(reading.path("month"), "index month"),
                            looseNumber(reading.path("value")),
                            booleanFromJson(orElse(reading.path("provisional"), false), "provisional"));
                }));
    }

    static ObjectNode adjustmentTermsToJson(AdjustmentTerms terms) {
        ObjectNode json = Json.object();
        json.put("baseDate", dateToJson(terms.baseDate()));
        json.put("nonAdjustable", percentToJson(terms.nonAdjustable()));
        json.set("weightings", listToJson(terms.weightings(), weighting -> {
            ObjectNode entry = Json.object();
            entry.put("indexId", weighting.indexId());
            entry.put("weight", percentToJson(weighting.weight()));
            return entry;
        }));
        if (terms.lagMonths() > 0) {
            json.put("lagMonths", terms.lagMonths());
        }
        if (terms.perPeriod()) {
            json.put("appliedTo", "period");
        }
        return json;
    }

    /** The clause, or none when the header carries {@code null} or nothing. */
    static Optional<AdjustmentTerms> adjustmentTermsFromJson(JsonNode value) {
        return optional(value, FluctuationCodec::termsFromJson);
    }

    private static AdjustmentTerms termsFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "price adjustment terms");
        return AdjustmentTerms.build(
                        dateFromJson(raw.path("baseDate")),
                        percentFromJson(orElse(raw.path("nonAdjustable"), 0)),
                        listFromJson(orElse(raw.path("weightings"), List.of()), "weightings", entry -> {
                            JsonNode weighting = objectFromJson(entry, "weighting");
                            return new Weighting(
                                    needString(weighting.path("indexId"), "index id"),
                                    percentFromJson(weighting.path("weight")));
                        }),
                        wholeNumber(raw.path("lagMonths"), "index lag", 0))
                .withPerPeriod(appliedPerPeriod(raw.path("appliedTo")));
    }

    /** {@code period} or {@code cumulative}, cumulative when the clause does not say. */
    private static boolean appliedPerPeriod(JsonNode value) {
        String text = optionalString(value);
        if (text.isEmpty() || text.equals("cumulative")) {
            return false;
        }
        if (text.equals("period")) {
            return true;
        }
        throw new BadInputException(
                "a price adjustment applies to each period or to the cumulative valuation",
                Details.of("appliedTo", text));
    }
}
