package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.arrayFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.currencyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.isAbsent;
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
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.orNull;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.textOf;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.wholeNumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.JobSettings;
import io.github.rkaushikdev.chainageworks.store.JobSettingsInput;
import io.github.rkaushikdev.chainageworks.store.Repository;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.valuation.AdvancePayment;
import io.github.rkaushikdev.chainageworks.valuation.InterimTerms;
import io.github.rkaushikdev.chainageworks.valuation.RetentionTerms;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A whole job as one JSON document, and back.
 *
 * <p>The document is the job file. Its keys, their order and the text of its numbers are fixed, so a
 * file written by any release with the same {@link #DOCUMENT_VERSION} loads, and two saves of the same
 * job are the same bytes. A register missing from a file reads as empty, because a file written before
 * the register existed has to keep loading.
 */
public final class JobCodec {

    /** Bumped whenever the shape on disk changes in a way a reader has to know. */
    public static final int DOCUMENT_VERSION = 1;

    private JobCodec() {}

    /**
     * One register, and how it goes to and from the file.
     *
     * @param key the document key
     * @param label what the register is called in an error message
     * @param of where the register lives on a job
     * @param toJson how one record is written
     * @param fromJson how one record is read
     */
    private record Register<T>(
            String key,
            String label,
            Function<Job, Repository<T>> of,
            Function<T, ObjectNode> toJson,
            Function<JsonNode, T> fromJson) {

        ArrayNode write(Job job) {
            return listToJson(of.apply(job).all(), toJson);
        }

        void read(Job job, List<JsonNode> entries) {
            of.apply(job).putAll(entries.stream().map(fromJson).toList());
        }
    }

    /** The registers written before the rules of credit and the inspection plans. */
    private static final List<Register<?>> LEADING = List.of(
            new Register<>(
                    "alignments",
                    "alignments",
                    Job::alignments,
                    WorksCodec::alignmentToJson,
                    WorksCodec::alignmentFromJson),
            new Register<>(
                    "billRevisions",
                    "bill revisions",
                    Job::billRevisions,
                    WorksCodec::revisionToJson,
                    WorksCodec::revisionFromJson),
            new Register<>(
                    "packages",
                    "work packages",
                    Job::packages,
                    WorksCodec::packageToJson,
                    WorksCodec::packageFromJson));

    /** The registers written after the rules of credit and the inspection plans. */
    private static final List<Register<?>> TRAILING = List.of(
            new Register<>(
                    "signOffs", "sign offs", Job::signOffs, WorksCodec::signOffToJson, WorksCodec::signOffFromJson),
            new Register<>("ncrs", "non conformances", Job::ncrs, WorksCodec::ncrToJson, WorksCodec::ncrFromJson),
            new Register<>(
                    "records", "survey records", Job::records, WorksCodec::recordToJson, WorksCodec::recordFromJson),
            new Register<>(
                    "materials",
                    "materials on site",
                    Job::materials,
                    WorksCodec::materialsToJson,
                    WorksCodec::materialsFromJson),
            new Register<>(
                    "variations",
                    "variations",
                    Job::variations,
                    WorksCodec::variationToJson,
                    WorksCodec::variationFromJson),
            new Register<>(
                    "dayworkSheets",
                    "daywork sheets",
                    Job::dayworkSheets,
                    DayworkCodec::dayworkSheetToJson,
                    DayworkCodec::dayworkSheetFromJson),
            new Register<>(
                    "delayEvents",
                    "delay events",
                    Job::delayEvents,
                    DelayCodec::delayEventToJson,
                    DelayCodec::delayEventFromJson),
            new Register<>("defects", "defects", Job::defects, DefectCodec::defectToJson, DefectCodec::defectFromJson),
            new Register<>(
                    "subcontracts",
                    "subcontracts",
                    Job::subcontracts,
                    SubcontractCodec::subcontractToJson,
                    SubcontractCodec::subcontractFromJson),
            new Register<>(
                    "paymentApplications",
                    "payment applications",
                    Job::paymentApplications,
                    SubcontractCodec::applicationToJson,
                    SubcontractCodec::applicationFromJson),
            new Register<>(
                    "paymentNotices",
                    "payment notices",
                    Job::paymentNotices,
                    SubcontractCodec::noticeToJson,
                    SubcontractCodec::noticeFromJson),
            new Register<>(
                    "payLessNotices",
                    "pay less notices",
                    Job::payLessNotices,
                    SubcontractCodec::payLessToJson,
                    SubcontractCodec::payLessFromJson),
            new Register<>(
                    "resourceReturns",
                    "resource returns",
                    Job::resourceReturns,
                    ResourceCodec::resourceReturnToJson,
                    ResourceCodec::resourceReturnFromJson),
            new Register<>(
                    "indices",
                    "index series",
                    Job::indices,
                    FluctuationCodec::indexSeriesToJson,
                    FluctuationCodec::indexSeriesFromJson),
            new Register<>(
                    "contraCharges",
                    "contra charges",
                    Job::contraCharges,
                    SubcontractCodec::contraChargeToJson,
                    SubcontractCodec::contraChargeFromJson),
            new Register<>(
                    "haulSites",
                    "haul sites",
                    Job::haulSites,
                    EarthworksCodec::haulSiteToJson,
                    EarthworksCodec::haulSiteFromJson),
            new Register<>(
                    "earlyWarnings",
                    "early warnings",
                    Job::earlyWarnings,
                    RiskCodec::earlyWarningToJson,
                    RiskCodec::earlyWarningFromJson));

    /**
     * Every register that goes to the file the same way.
     *
     * <p>Rules of credit and inspection plans are deliberately not here. The profiles have to be cleared
     * before they are read, because a job file carries its own and the standard ones are seeded into a
     * new job, and the plans have to be read against the profiles that were just loaded. Both are one
     * off and both would have to be special cased inside the loop, which is exactly the shape this table
     * exists to avoid.
     */
    private static final List<Register<?>> REGISTERS =
            Stream.concat(LEADING.stream(), TRAILING.stream()).toList();

    /** The job as a document. */
    public static ObjectNode toJson(Job job) {
        ObjectNode doc = Json.object();
        doc.put("version", DOCUMENT_VERSION);
        doc.set("settings", settingsToJson(job.settings()));
        LEADING.forEach(register -> doc.set(register.key(), register.write(job)));
        doc.set("profiles", listToJson(job.profiles().all(), WorksCodec::profileToJson));
        doc.set("plans", listToJson(job.plans().all(), WorksCodec::planToJson));
        TRAILING.forEach(register -> doc.set(register.key(), register.write(job)));
        doc.set("holidays", listToJson(job.holidays(), day -> TextNode.valueOf(dateToJson(day))));
        doc.set("standdowns", listToJson(job.standdowns(), WorksCodec::standdownToJson));
        return doc;
    }

    /**
     * The job a document describes.
     *
     * @throws BadInputException when the value is not a job document, was written by another version,
     *     or holds a record the domain refuses
     */
    public static Job fromJson(JsonNode value) {
        JsonNode doc = objectFromJson(value, "job document");
        JsonNode version = doc.path("version");
        if (!version.isNumber() || version.doubleValue() != DOCUMENT_VERSION) {
            throw new BadInputException(
                    "this file was written by a different version",
                    Details.of("found", textOf(version), "expected", DOCUMENT_VERSION));
        }
        Job job = Job.create(settingsFromJson(objectFromJson(doc.path("settings"), "settings")));

        job.profiles().clear();
        List<CreditProfile> profiles =
                listFromJson(doc.path("profiles"), "rules of credit", WorksCodec::profileFromJson);
        job.profiles().putAll(profiles);
        job.plans()
                .putAll(listFromJson(
                        doc.path("plans"), "inspection plans", entry -> WorksCodec.planFromJson(entry, profiles)));
        for (Register<?> register : REGISTERS) {
            register.read(job, arrayFromJson(orElse(doc.path(register.key()), List.of()), register.label()));
        }
        job.setHolidays(listFromJson(doc.path("holidays"), "holidays", ValueCodec::dateFromJson));
        job.setStanddowns(listFromJson(doc.path("standdowns"), "standdowns", WorksCodec::standdownFromJson));
        return job;
    }

    private static ObjectNode settingsToJson(JobSettings settings) {
        ObjectNode json = Json.object();
        json.put("id", settings.id());
        json.put("name", settings.name());
        json.put("currency", settings.currency().code());
        json.set("contractSum", moneyToJson(settings.contractSum()));
        ObjectNode retention = json.putObject("retention");
        retention.put("rate", percentToJson(settings.retention().rate()));
        retention.put("limit", percentToJson(settings.retention().limit()));
        retention.put("releaseOnCompletion", percentToJson(settings.retention().releaseOnCompletion()));
        json.set("retentionBond", orNull(settings.retentionBond().map(BondCodec::retentionBondToJson)));
        json.set("materialsAllowance", percentToJson(settings.materialsAllowance()));
        json.set("daywork", orNull(settings.daywork().map(DayworkCodec::dayworkScheduleToJson)));
        json.set("completion", orNull(settings.completion().map(DelayCodec::completionToJson)));
        json.set("defectsPeriodEnds", optionalDateToJson(settings.defectsPeriodEnds()));
        json.set("outputAllowances", listToJson(settings.outputAllowances(), ResourceCodec::allowanceToJson));
        json.set("adjustment", orNull(settings.adjustment().map(FluctuationCodec::adjustmentTermsToJson)));
        json.put("contraNotifyDays", settings.contraNotifyDays());
        json.put("riskMeetingDays", settings.riskMeetingDays());
        json.set("haulRate", moneyToJson(settings.haulRate()));
        json.set("haulLinks", listToJson(settings.haulLinks(), EarthworksCodec::haulLinkToJson));
        json.set("tolerances", listToJson(settings.tolerances().rules(), rule -> {
            ObjectNode entry = Json.object();
            // A class on its own keeps the field older job files use, a heading below it says so.
            Optional<ItemCode> heading = rule.heading();
            if (heading.isPresent() && heading.get().depth() > 0) {
                entry.put("heading", heading.get().format());
            } else {
                entry.set("workClass", ValueCodec.text(heading.map(ItemCode::workClass)));
            }
            entry.put("allowance", percentToJson(rule.allowance()));
            return entry;
        }));
        ArrayNode workingDays = json.putArray("workingDays");
        settings.workingDays().forEach(workingDays::add);
        json.put("cutOffDay", settings.cutOffDay());
        json.put("issueDays", settings.issueDays());
        json.put("paymentDays", settings.paymentDays());
        json.set("substantialCompletionOn", optionalDateToJson(settings.substantialCompletionOn()));
        json.set("defectsCertificateOn", optionalDateToJson(settings.defectsCertificateOn()));
        settings.interimTerms()
                .minimumCertificate()
                .ifPresent(minimum -> json.set("minimumCertificate", moneyToJson(minimum)));
        settings.interimTerms().advance().ifPresent(advance -> {
            ObjectNode entry = json.putObject("advancePayment");
            entry.set("amount", moneyToJson(advance.amount()));
            entry.put("paidOn", dateToJson(advance.paidOn()));
            entry.put("repaymentFrom", percentToJson(advance.repaymentFrom()));
            entry.put("repaymentRate", percentToJson(advance.repaymentRate()));
        });
        return json;
    }

    private static JobSettingsInput settingsFromJson(JsonNode settings) {
        JsonNode retention = objectFromJson(settings.path("retention"), "retention terms");
        String id = needString(settings.path("id"), "job id");
        String name = optionalString(settings.path("name"));
        Currency currency = currencyFromJson(settings.path("currency"));
        Money contractSum = moneyFromJson(settings.path("contractSum"));
        RetentionTerms terms = new RetentionTerms(
                percentFromJson(retention.path("rate")),
                percentFromJson(retention.path("limit")),
                percentFromJson(retention.path("releaseOnCompletion")));
        return new JobSettingsInput(
                id,
                name,
                currency,
                contractSum,
                terms,
                BondCodec.retentionBondFromJson(settings.path("retentionBond")).orElse(null),
                optional(settings.path("materialsAllowance"), ValueCodec::percentFromJson)
                        .orElse(null),
                DayworkCodec.dayworkScheduleFromJson(settings.path("daywork")).orElse(null),
                DelayCodec.completionFromJson(settings.path("completion")).orElse(null),
                optionalDateFromJson(settings.path("defectsPeriodEnds")).orElse(null),
                listFromJson(
                        orElse(settings.path("outputAllowances"), List.of()),
                        "output allowances",
                        ResourceCodec::allowanceFromJson),
                FluctuationCodec.adjustmentTermsFromJson(settings.path("adjustment"))
                        .orElse(null),
                wholeNumber(
                        settings.path("contraNotifyDays"),
                        "contra notice days",
                        Subcontract.DEFAULT_CONTRA_NOTIFY_DAYS),
                wholeNumber(settings.path("riskMeetingDays"), "risk meeting days", EarlyWarning.DEFAULT_MEETING_DAYS),
                isAbsent(settings.path("haulRate"))
                        ? Money.zero(currencyFromJson(settings.path("currency")))
                        : moneyFromJson(settings.path("haulRate")),
                listFromJson(
                        orElse(settings.path("haulLinks"), List.of()), "haul links", EarthworksCodec::haulLinkFromJson),
                new ToleranceSet(listFromJson(settings.path("tolerances"), "tolerance rules", entry -> {
                    JsonNode rule = objectFromJson(entry, "tolerance rule");
                    Optional<ItemCode> heading = isAbsent(rule.path("heading"))
                            ? optional(
                                    rule.path("workClass"),
                                    workClass -> ItemCode.of(needString(workClass, "work class")))
                            : Optional.of(ItemCode.parse(needString(rule.path("heading"), "heading")));
                    return new ToleranceSet.Rule(heading, percentFromJson(rule.path("allowance")));
                })),
                listFromJson(settings.path("workingDays"), "working days", day -> wholeNumber(day, "working day")),
                wholeNumber(settings.path("cutOffDay"), "cut off day"),
                wholeNumber(settings.path("issueDays"), "issue days"),
                wholeNumber(settings.path("paymentDays"), "payment days"),
                optionalDateFromJson(settings.path("substantialCompletionOn")).orElse(null),
                optionalDateFromJson(settings.path("defectsCertificateOn")).orElse(null),
                interimTermsFromJson(settings));
    }

    /** The minimum certificate and the advance payment, each absent from a job that has none. */
    private static InterimTerms interimTermsFromJson(JsonNode settings) {
        InterimTerms terms = optional(settings.path("minimumCertificate"), ValueCodec::moneyFromJson)
                .map(InterimTerms::withMinimum)
                .orElse(InterimTerms.NONE);
        return terms.withAdvance(optional(settings.path("advancePayment"), value -> {
                    JsonNode advance = objectFromJson(value, "advance payment");
                    return AdvancePayment.build(
                            moneyFromJson(advance.path("amount")),
                            ValueCodec.dateFromJson(advance.path("paidOn")),
                            optional(advance.path("repaymentFrom"), ValueCodec::percentFromJson)
                                    .orElse(null),
                            optional(advance.path("repaymentRate"), ValueCodec::percentFromJson)
                                    .orElse(null));
                })
                .orElse(null));
    }
}
