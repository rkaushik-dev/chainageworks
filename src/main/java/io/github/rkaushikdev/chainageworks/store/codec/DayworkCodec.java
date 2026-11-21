package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.currencyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.looseNumber;
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
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkRate;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkScheduleInput;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheetInput;
import io.github.rkaushikdev.chainageworks.domain.daywork.LabourEntry;
import io.github.rkaushikdev.chainageworks.domain.daywork.MaterialEntry;
import io.github.rkaushikdev.chainageworks.domain.daywork.PlantEntry;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.List;
import java.util.Optional;

/**
 * The daywork half of the job document, kept in its own class.
 *
 * <p>A sheet has three kinds of line and a schedule has two groups of rates, which is enough shape
 * that folding it into the main codec made that class hard to read for anything else.
 */
final class DayworkCodec {

    private DayworkCodec() {}

    private static ArrayNode ratesToJson(List<DayworkRate> group) {
        return listToJson(group, entry -> {
            ObjectNode json = Json.object();
            json.put("id", entry.id());
            json.put("description", entry.description());
            json.set("rate", rateToJson(entry.rate()));
            return json;
        });
    }

    private static List<DayworkRate> ratesFromJson(JsonNode value, String what) {
        return listFromJson(value, what, entry -> {
            JsonNode raw = objectFromJson(entry, "daywork rate");
            return new DayworkRate(
                    needString(raw.path("id"), "rate id"),
                    optionalString(raw.path("description")),
                    rateFromJson(raw.path("rate")));
        });
    }

    static ObjectNode dayworkScheduleToJson(DayworkSchedule schedule) {
        ObjectNode json = Json.object();
        json.put("currency", schedule.currency().code());
        json.set("labour", ratesToJson(schedule.labour()));
        json.set("plant", ratesToJson(schedule.plant()));
        ObjectNode additions = json.putObject("additions");
        additions.put("labour", percentToJson(schedule.additions().labour()));
        additions.put("plant", percentToJson(schedule.additions().plant()));
        additions.put("materials", percentToJson(schedule.additions().materials()));
        json.set("overtimeFactor", Json.number(schedule.overtimeFactor()));
        return json;
    }

    /** The schedule, or none when the header carries {@code null} or nothing. */
    static Optional<DayworkSchedule> dayworkScheduleFromJson(JsonNode value) {
        return optional(value, DayworkCodec::scheduleFromJson);
    }

    private static DayworkSchedule scheduleFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "daywork schedule");
        JsonNode additions = objectFromJson(raw.path("additions"), "daywork additions");
        return DayworkSchedule.build(new DayworkScheduleInput(
                currencyFromJson(raw.path("currency")),
                ratesFromJson(raw.path("labour"), "daywork labour rates"),
                ratesFromJson(raw.path("plant"), "daywork plant rates"),
                percentFromJson(additions.path("labour")),
                percentFromJson(additions.path("plant")),
                percentFromJson(additions.path("materials")),
                looseNumber(raw.path("overtimeFactor"))));
    }

    static ObjectNode dayworkSheetToJson(DayworkSheet sheet) {
        ObjectNode json = Json.object();
        json.put("id", sheet.id());
        json.put("reference", sheet.reference());
        json.put("packageId", sheet.packageId());
        json.put("instruction", sheet.instruction());
        json.put("workedOn", dateToJson(sheet.workedOn()));
        json.set("signedOn", optionalDateToJson(sheet.signedOn()));
        json.put("signedBy", sheet.signedBy());
        json.set("labour", listToJson(sheet.labour(), entry -> {
            ObjectNode line = Json.object();
            line.put("rateId", entry.rateId());
            line.put("people", entry.people());
            line.set("hours", Json.number(entry.hours()));
            line.set("overtimeHours", Json.number(entry.overtimeHours()));
            return line;
        }));
        json.set("plant", listToJson(sheet.plant(), entry -> {
            ObjectNode line = Json.object();
            line.put("rateId", entry.rateId());
            line.set("hours", Json.number(entry.hours()));
            line.set("overtimeHours", Json.number(entry.overtimeHours()));
            return line;
        }));
        json.set("materials", listToJson(sheet.materials(), entry -> {
            ObjectNode line = Json.object();
            line.put("description", entry.description());
            line.set("value", moneyToJson(entry.value()));
            return line;
        }));
        return json;
    }

    static DayworkSheet dayworkSheetFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "daywork sheet");
        return DayworkSheet.build(new DayworkSheetInput(
                needString(raw.path("id"), "sheet id"),
                optionalString(raw.path("reference")),
                needString(raw.path("packageId"), "package id"),
                optionalString(raw.path("instruction")),
                dateFromJson(raw.path("workedOn")),
                optionalDateFromJson(raw.path("signedOn")).orElse(null),
                optionalString(raw.path("signedBy")),
                listFromJson(orElse(raw.path("labour"), List.of()), "labour lines", entry -> {
                    JsonNode line = objectFromJson(entry, "labour line");
                    return new LabourEntry(
                            needString(line.path("rateId"), "rate id"),
                            wholeNumber(line.path("people"), "people"),
                            looseNumber(line.path("hours")),
                            looseNumber(orElse(line.path("overtimeHours"), 0)));
                }),
                listFromJson(orElse(raw.path("plant"), List.of()), "plant lines", entry -> {
                    JsonNode line = objectFromJson(entry, "plant line");
                    return new PlantEntry(
                            needString(line.path("rateId"), "rate id"),
                            looseNumber(line.path("hours")),
                            looseNumber(orElse(line.path("overtimeHours"), 0)));
                }),
                listFromJson(orElse(raw.path("materials"), List.of()), "material lines", entry -> {
                    JsonNode line = objectFromJson(entry, "material line");
                    return new MaterialEntry(
                            optionalString(line.path("description")), moneyFromJson(line.path("value")));
                })));
    }
}
