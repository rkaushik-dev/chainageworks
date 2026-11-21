package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.booleanFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.codeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.isAbsent;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.needString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.orElse;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.textsToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraChargeInput;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraReason;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PayLessNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentTerms;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.subcontract.SubcontractInput;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.List;

/** The subcontract registers: the orders, the applications, the notices and the contra charges. */
final class SubcontractCodec {

    private SubcontractCodec() {}

    private static ObjectNode termsToJson(PaymentTerms terms) {
        ObjectNode json = Json.object();
        json.put("dueDays", terms.dueDays());
        json.put("noticeDays", terms.noticeDays());
        json.put("payLessDays", terms.payLessDays());
        json.put("finalDays", terms.finalDays());
        json.put("retention", percentToJson(terms.retention()));
        return json;
    }

    /**
     * Read the payment terms back.
     *
     * <p>Every window is optional on the way in and falls back to the statutory default, because a job
     * file written before a term existed has to keep loading. The alternative is a file that refuses to
     * open on the day the schema grows a field, which is the worst possible time to find out.
     */
    private static PaymentTerms termsFromJson(JsonNode value) {
        PaymentTerms base = PaymentTerms.build(PaymentTerms.DEFAULT);
        if (isAbsent(value)) {
            return base;
        }
        JsonNode raw = objectFromJson(value, "payment terms");
        JsonNode retention = raw.path("retention");
        return PaymentTerms.build(new PaymentTerms(
                wholeNumber(raw.path("dueDays"), "dueDays", base.dueDays()),
                wholeNumber(raw.path("noticeDays"), "noticeDays", base.noticeDays()),
                wholeNumber(raw.path("payLessDays"), "payLessDays", base.payLessDays()),
                wholeNumber(raw.path("finalDays"), "finalDays", base.finalDays()),
                isAbsent(retention) ? base.retention() : percentFromJson(retention)));
    }

    static ObjectNode subcontractToJson(Subcontract subcontract) {
        ObjectNode json = Json.object();
        json.put("id", subcontract.id());
        json.put("name", subcontract.name());
        json.put("contractor", subcontract.contractor());
        json.set("orderValue", moneyToJson(subcontract.orderValue()));
        json.set("packageIds", textsToJson(subcontract.packageIds()));
        ArrayNode codes = json.putArray("codes");
        subcontract.codes().forEach(code -> codes.add(codeToJson(code)));
        json.set("terms", termsToJson(subcontract.terms()));
        json.put("placedOn", dateToJson(subcontract.placedOn()));
        return json;
    }

    static Subcontract subcontractFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "subcontract");
        return Subcontract.build(new SubcontractInput(
                needString(raw.path("id"), "subcontract id"),
                optionalString(raw.path("name")),
                needString(raw.path("contractor"), "contractor"),
                moneyFromJson(raw.path("orderValue")),
                listFromJson(
                        orElse(raw.path("packageIds"), List.of()), "package ids", id -> needString(id, "package id")),
                listFromJson(orElse(raw.path("codes"), List.of()), "bill items", ValueCodec::codeFromJson),
                termsFromJson(raw.path("terms")),
                dateFromJson(raw.path("placedOn"))));
    }

    static ObjectNode applicationToJson(PaymentApplication application) {
        ObjectNode json = Json.object();
        json.put("id", application.id());
        json.put("subcontractId", application.subcontractId());
        json.put("periodNumber", application.periodNumber());
        json.put("appliedOn", dateToJson(application.appliedOn()));
        json.set("gross", moneyToJson(application.gross()));
        json.set("previouslyPaid", moneyToJson(application.previouslyPaid()));
        return json;
    }

    static PaymentApplication applicationFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "payment application");
        return new PaymentApplication(
                needString(raw.path("id"), "application id"),
                needString(raw.path("subcontractId"), "subcontract id"),
                wholeNumber(raw.path("periodNumber"), "period number"),
                dateFromJson(raw.path("appliedOn")),
                moneyFromJson(raw.path("gross")),
                moneyFromJson(raw.path("previouslyPaid")));
    }

    static ObjectNode noticeToJson(PaymentNotice notice) {
        ObjectNode json = Json.object();
        json.put("applicationId", notice.applicationId());
        json.put("servedOn", dateToJson(notice.servedOn()));
        json.set("valued", moneyToJson(notice.valued()));
        return json;
    }

    static PaymentNotice noticeFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "payment notice");
        return new PaymentNotice(
                needString(raw.path("applicationId"), "application id"),
                dateFromJson(raw.path("servedOn")),
                moneyFromJson(raw.path("valued")));
    }

    static ObjectNode payLessToJson(PayLessNotice notice) {
        ObjectNode json = Json.object();
        json.put("applicationId", notice.applicationId());
        json.put("servedOn", dateToJson(notice.servedOn()));
        json.set("amount", moneyToJson(notice.amount()));
        json.put("reason", notice.reason());
        return json;
    }

    static PayLessNotice payLessFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "pay less notice");
        return new PayLessNotice(
                needString(raw.path("applicationId"), "application id"),
                dateFromJson(raw.path("servedOn")),
                moneyFromJson(raw.path("amount")),
                needString(raw.path("reason"), "pay less reason"));
    }

    private static ContraReason reasonFromJson(JsonNode value) {
        return wire(
                orElse(value, ContraReason.OTHER.wire()),
                "contra reason",
                ContraReason::fromWire,
                text -> new BadInputException(
                        "not a reason for a contra charge this tool knows", Details.of("got", text)));
    }

    static ObjectNode contraChargeToJson(ContraCharge charge) {
        ObjectNode json = Json.object();
        json.put("id", charge.id());
        json.put("subcontractId", charge.subcontractId());
        json.put("reference", charge.reference());
        json.put("reason", charge.reason().wire());
        json.put("description", charge.description());
        json.set("amount", moneyToJson(charge.amount()));
        json.put("incurredOn", dateToJson(charge.incurredOn()));
        json.set("notifiedOn", optionalDateToJson(charge.notifiedOn()));
        json.put("disputed", charge.disputed());
        return json;
    }

    static ContraCharge contraChargeFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "contra charge");
        return ContraCharge.build(new ContraChargeInput(
                needString(raw.path("id"), "charge id"),
                needString(raw.path("subcontractId"), "subcontract id"),
                optionalString(raw.path("reference")),
                reasonFromJson(raw.path("reason")),
                needString(raw.path("description"), "description"),
                moneyFromJson(raw.path("amount")),
                dateFromJson(raw.path("incurredOn")),
                optionalDateFromJson(raw.path("notifiedOn")).orElse(null),
                booleanFromJson(orElse(raw.path("disputed"), false), "disputed")));
    }
}
