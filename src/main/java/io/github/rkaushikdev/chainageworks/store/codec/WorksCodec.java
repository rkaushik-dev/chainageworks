package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.booleanFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.codeFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.codeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.currencyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.listToJson;
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
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.quantityFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.quantityToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rangeFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rangeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.stationFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.stationToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.text;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment.ChainageEquation;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditStep;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPoint;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrInput;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrStatus;
import io.github.rkaushikdev.chainageworks.domain.itp.PointType;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffInput;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffOutcome;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackageInput;
import io.github.rkaushikdev.chainageworks.domain.survey.MeasurementMethod;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecordInput;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationInput;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationItem;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationStatus;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.valuation.MaterialsInput;
import io.github.rkaushikdev.chainageworks.valuation.MaterialsOnSite;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The registers of the works themselves: the alignment, the bill, the packages and their rules of
 * credit, the inspection plans and what was signed off against them, the survey book, the materials on
 * site, the variations and the standdowns.
 */
final class WorksCodec {

    private WorksCodec() {}

    private static <E> E known(JsonNode value, String what, Function<String, Optional<E>> fromWire) {
        return wire(
                value,
                what,
                fromWire,
                text -> new BadInputException(text + " is not a known " + what, Details.of("got", text)));
    }

    static ObjectNode alignmentToJson(Alignment alignment) {
        ObjectNode json = Json.object();
        json.put("id", alignment.id());
        json.put("name", alignment.name());
        json.put("start", stationToJson(alignment.start()));
        json.put("end", stationToJson(alignment.end()));
        json.set("equations", listToJson(alignment.equations(), equation -> {
            ObjectNode entry = Json.object();
            entry.put("back", stationToJson(equation.back()));
            entry.put("ahead", stationToJson(equation.ahead()));
            return entry;
        }));
        return json;
    }

    static Alignment alignmentFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "alignment");
        List<ChainageEquation> equations =
                listFromJson(orElse(raw.path("equations"), List.of()), "chainage equations", entry -> {
                    JsonNode equation = objectFromJson(entry, "chainage equation");
                    return new ChainageEquation(
                            stationFromJson(equation.path("back")), stationFromJson(equation.path("ahead")));
                });
        return Alignment.build(
                needString(raw.path("id"), "alignment id"),
                optionalString(raw.path("name")),
                stationFromJson(raw.path("start")),
                stationFromJson(raw.path("end")),
                equations);
    }

    private static ObjectNode billItemToJson(BillItem item) {
        ObjectNode json = Json.object();
        json.put("code", codeToJson(item.code()));
        json.put("description", item.description());
        json.set("rate", rateToJson(item.rate()));
        json.set("quantity", quantityToJson(item.quantity()));
        json.put("kind", item.kind().wire());
        json.set("chainage", rangeToJson(item.chainage()));
        return json;
    }

    private static BillItem billItemFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "bill item");
        return BillItem.build(new BillItemInput(
                codeFromJson(raw.path("code")),
                needString(raw.path("description"), "description"),
                rateFromJson(raw.path("rate")),
                quantityFromJson(raw.path("quantity")),
                known(raw.path("kind"), "item kind", BillItemKind::fromWire),
                optional(raw.path("chainage"), ValueCodec::rangeFromJson)));
    }

    static ObjectNode revisionToJson(BillRevision revision) {
        ObjectNode json = Json.object();
        json.put("reference", revision.reference());
        json.put("effectiveFrom", dateToJson(revision.effectiveFrom()));
        json.put("note", revision.note());
        ObjectNode bill = json.putObject("bill");
        bill.put("id", revision.bill().id());
        bill.put("title", revision.bill().title());
        bill.put("currency", revision.bill().currency().code());
        bill.set("items", listToJson(revision.bill().items(), WorksCodec::billItemToJson));
        return json;
    }

    static BillRevision revisionFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "bill revision");
        JsonNode bill = objectFromJson(raw.path("bill"), "bill");
        String reference = needString(raw.path("reference"), "revision reference");
        LocalDate effectiveFrom = dateFromJson(raw.path("effectiveFrom"));
        String note = optionalString(raw.path("note"));
        return new BillRevision(
                reference,
                effectiveFrom,
                Bill.build(
                        needString(bill.path("id"), "bill id"),
                        optionalString(bill.path("title")),
                        currencyFromJson(bill.path("currency")),
                        listFromJson(bill.path("items"), "bill items", WorksCodec::billItemFromJson)),
                note);
    }

    static ObjectNode packageToJson(WorkPackage pkg) {
        ObjectNode json = Json.object();
        json.put("id", pkg.id());
        json.put("name", pkg.name());
        json.put("alignmentId", pkg.alignmentId());
        json.set("extent", rangeToJson(pkg.extent()));
        json.put("section", pkg.section());
        json.put("profileId", pkg.profileId());
        json.set("allocations", listToJson(pkg.allocations(), allocation -> {
            ObjectNode entry = Json.object();
            entry.put("code", codeToJson(allocation.code()));
            entry.set("quantity", quantityToJson(allocation.quantity()));
            return entry;
        }));
        return json;
    }

    static WorkPackage packageFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "work package");
        return WorkPackage.build(new WorkPackageInput(
                needString(raw.path("id"), "package id"),
                optionalString(raw.path("name")),
                needString(raw.path("alignmentId"), "alignment id"),
                rangeFromJson(raw.path("extent")),
                optionalString(raw.path("section")),
                needString(raw.path("profileId"), "rule of credit id"),
                listFromJson(raw.path("allocations"), "allocations", entry -> {
                    JsonNode allocation = objectFromJson(entry, "allocation");
                    return new PackageAllocation(
                            codeFromJson(allocation.path("code")), quantityFromJson(allocation.path("quantity")));
                })));
    }

    static ObjectNode profileToJson(CreditProfile profile) {
        ObjectNode json = Json.object();
        json.put("id", profile.id());
        json.put("name", profile.name());
        json.set("steps", listToJson(profile.steps(), step -> {
            ObjectNode entry = Json.object();
            entry.put("id", step.id());
            entry.put("name", step.name());
            entry.put("weight", percentToJson(step.weight()));
            entry.put("requiresPrevious", step.requiresPrevious());
            return entry;
        }));
        return json;
    }

    static CreditProfile profileFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "rule of credit");
        return CreditProfile.build(
                needString(raw.path("id"), "profile id"),
                optionalString(raw.path("name")),
                listFromJson(raw.path("steps"), "steps", entry -> {
                    JsonNode step = objectFromJson(entry, "step");
                    return new CreditStep(
                            needString(step.path("id"), "step id"),
                            optionalString(step.path("name")),
                            percentFromJson(step.path("weight")),
                            booleanFromJson(orElse(step.path("requiresPrevious"), false), "requiresPrevious"));
                }));
    }

    static ObjectNode planToJson(InspectionPlan plan) {
        ObjectNode json = Json.object();
        json.put("id", plan.id());
        json.put("name", plan.name());
        json.put("profileId", plan.profileId());
        json.set("points", listToJson(plan.points(), point -> {
            ObjectNode entry = Json.object();
            entry.put("id", point.id());
            entry.put("name", point.name());
            entry.put("type", point.type().wire());
            entry.put("stepId", point.stepId());
            entry.put("discipline", point.discipline());
            entry.put("reference", point.reference());
            return entry;
        }));
        return json;
    }

    /**
     * A plan, read against the rules of credit the same file carries.
     *
     * @throws BadInputException when the plan gates a rule of credit that is not among {@code profiles}
     */
    static InspectionPlan planFromJson(JsonNode value, List<CreditProfile> profiles) {
        JsonNode raw = objectFromJson(value, "inspection plan");
        String profileId = needString(raw.path("profileId"), "rule of credit id");
        CreditProfile profile = profiles.stream()
                .filter(p -> p.id().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new BadInputException(
                        "the plan gates a rule of credit the job does not carry", Details.of("profile", profileId)));
        return InspectionPlan.build(
                needString(raw.path("id"), "plan id"),
                optionalString(raw.path("name")),
                profileId,
                listFromJson(raw.path("points"), "inspection points", entry -> {
                    JsonNode point = objectFromJson(entry, "inspection point");
                    return new InspectionPoint(
                            needString(point.path("id"), "point id"),
                            optionalString(point.path("name")),
                            known(point.path("type"), "point type", PointType::fromWire),
                            needString(point.path("stepId"), "step id"),
                            optionalString(point.path("discipline"), "civil"),
                            optionalString(point.path("reference")));
                }),
                profile);
    }

    static ObjectNode signOffToJson(SignOff signOff) {
        ObjectNode json = Json.object();
        json.put("id", signOff.id());
        json.put("pointId", signOff.pointId());
        json.put("packageId", signOff.packageId());
        json.set("extent", rangeToJson(signOff.extent()));
        json.put("signedOn", dateToJson(signOff.signedOn()));
        json.put("by", signOff.by());
        json.put("outcome", signOff.outcome().wire());
        json.put("note", signOff.note());
        return json;
    }

    static SignOff signOffFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "sign off");
        return SignOff.build(new SignOffInput(
                needString(raw.path("id"), "sign off id"),
                needString(raw.path("pointId"), "point id"),
                needString(raw.path("packageId"), "package id"),
                rangeFromJson(raw.path("extent")),
                dateFromJson(raw.path("signedOn")),
                needString(raw.path("by"), "signatory"),
                known(orElse(raw.path("outcome"), "passed"), "sign off outcome", SignOffOutcome::fromWire),
                optionalString(raw.path("note"))));
    }

    static ObjectNode ncrToJson(Ncr ncr) {
        ObjectNode json = Json.object();
        json.put("id", ncr.id());
        json.put("packageId", ncr.packageId());
        json.set("extent", rangeToJson(ncr.extent()));
        json.set("stepId", text(ncr.stepId()));
        json.put("raisedOn", dateToJson(ncr.raisedOn()));
        json.set("settledOn", optionalDateToJson(ncr.settledOn()));
        json.put("status", ncr.status().wire());
        json.put("description", ncr.description());
        json.put("withholdsCredit", ncr.withholdsCredit());
        return json;
    }

    static Ncr ncrFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "non conformance");
        return Ncr.build(new NcrInput(
                needString(raw.path("id"), "ncr id"),
                needString(raw.path("packageId"), "package id"),
                rangeFromJson(raw.path("extent")),
                optional(raw.path("stepId"), step -> needString(step, "step id"))
                        .orElse(null),
                dateFromJson(raw.path("raisedOn")),
                optionalDateFromJson(raw.path("settledOn")).orElse(null),
                known(orElse(raw.path("status"), "open"), "ncr status", NcrStatus::fromWire),
                needString(raw.path("description"), "description"),
                booleanFromJson(orElse(raw.path("withholdsCredit"), true), "withholdsCredit")));
    }

    static ObjectNode recordToJson(SurveyRecord record) {
        ObjectNode json = Json.object();
        json.put("id", record.id());
        json.put("packageId", record.packageId());
        json.put("code", codeToJson(record.code()));
        json.put("stepId", record.stepId());
        json.set("extent", rangeToJson(record.extent()));
        json.set("quantity", quantityToJson(record.quantity()));
        json.put("measuredOn", dateToJson(record.measuredOn()));
        json.put("bookedBy", record.bookedBy());
        json.put("method", record.method().wire());
        json.set("supersedes", text(record.supersedes()));
        json.put("note", record.note());
        return json;
    }

    static SurveyRecord recordFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "survey record");
        return SurveyRecord.build(new SurveyRecordInput(
                needString(raw.path("id"), "record id"),
                needString(raw.path("packageId"), "package id"),
                codeFromJson(raw.path("code")),
                needString(raw.path("stepId"), "step id"),
                rangeFromJson(raw.path("extent")),
                quantityFromJson(raw.path("quantity")),
                dateFromJson(raw.path("measuredOn")),
                needString(raw.path("bookedBy"), "booked by"),
                known(orElse(raw.path("method"), "tape"), "measurement method", MeasurementMethod::fromWire),
                optional(raw.path("supersedes"), id -> needString(id, "superseded record"))
                        .orElse(null),
                optionalString(raw.path("note"))));
    }

    static ObjectNode materialsToJson(MaterialsOnSite materials) {
        ObjectNode json = Json.object();
        json.put("id", materials.id());
        json.put("packageId", materials.packageId());
        json.put("code", codeToJson(materials.code()));
        json.put("description", materials.description());
        json.set("quantity", quantityToJson(materials.quantity()));
        json.set("value", moneyToJson(materials.value()));
        json.put("deliveredOn", dateToJson(materials.deliveredOn()));
        json.set("vestedOn", optionalDateToJson(materials.vestedOn()));
        json.set("incorporatedOn", optionalDateToJson(materials.incorporatedOn()));
        json.put("protectedOnSite", materials.protectedOnSite());
        return json;
    }

    static MaterialsOnSite materialsFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "materials on site");
        return MaterialsOnSite.build(new MaterialsInput(
                needString(raw.path("id"), "materials id"),
                needString(raw.path("packageId"), "package id"),
                codeFromJson(raw.path("code")),
                needString(raw.path("description"), "description"),
                quantityFromJson(raw.path("quantity")),
                moneyFromJson(raw.path("value")),
                dateFromJson(raw.path("deliveredOn")),
                optionalDateFromJson(raw.path("vestedOn")).orElse(null),
                optionalDateFromJson(raw.path("incorporatedOn")).orElse(null),
                booleanFromJson(orElse(raw.path("protectedOnSite"), true), "protectedOnSite")));
    }

    static ObjectNode variationToJson(Variation variation) {
        ObjectNode json = Json.object();
        json.put("id", variation.id());
        json.put("reference", variation.reference());
        json.put("title", variation.title());
        json.put("status", variation.status().wire());
        json.set("instructedOn", optionalDateToJson(variation.instructedOn()));
        json.set("agreedOn", optionalDateToJson(variation.agreedOn()));
        json.set("extent", rangeToJson(variation.extent()));
        json.set("items", listToJson(variation.items(), item -> {
            ObjectNode entry = Json.object();
            entry.put("code", codeToJson(item.code()));
            entry.set("quantityChange", quantityToJson(item.quantityChange()));
            entry.set("rate", rateToJson(item.rate()));
            entry.put("basis", item.basis().wire());
            return entry;
        }));
        return json;
    }

    static Variation variationFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "variation");
        String id = needString(raw.path("id"), "variation id");
        String reference = optionalString(raw.path("reference"));
        String title = optionalString(raw.path("title"));
        VariationStatus status =
                known(orElse(raw.path("status"), "proposed"), "variation status", VariationStatus::fromWire);
        LocalDate instructedOn = optionalDateFromJson(raw.path("instructedOn")).orElse(null);
        LocalDate agreedOn = optionalDateFromJson(raw.path("agreedOn")).orElse(null);
        ChainageRange extent =
                optional(raw.path("extent"), ValueCodec::rangeFromJson).orElse(null);
        List<VariationItem> items = listFromJson(raw.path("items"), "variation items", entry -> {
            JsonNode item = objectFromJson(entry, "variation item");
            ItemCode code = codeFromJson(item.path("code"));
            return new VariationItem(
                    code,
                    quantityFromJson(item.path("quantityChange")),
                    optional(item.path("rate"), ValueCodec::rateFromJson),
                    known(item.path("basis"), "valuation basis", ValuationBasis::fromWire));
        });
        return Variation.build(new VariationInput(id, reference, title, status, instructedOn, agreedOn, items, extent));
    }

    static ObjectNode standdownToJson(Standdown standdown) {
        ObjectNode json = Json.object();
        json.put("date", dateToJson(standdown.date()));
        json.put("reason", standdown.reason().wire());
        json.put("note", standdown.note().orElse(""));
        return json;
    }

    static Standdown standdownFromJson(JsonNode value) {
        JsonNode raw = objectFromJson(value, "standdown");
        return new Standdown(
                dateFromJson(raw.path("date")),
                known(raw.path("reason"), "standdown reason", StanddownReason::fromWire),
                optionalString(raw.path("note")));
    }
}
