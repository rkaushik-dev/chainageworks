package io.github.rkaushikdev.chainageworks.domain.survey;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One measurement booked on site.
 *
 * <p>Records are never edited. A remeasure is a new record that supersedes the old one, and that
 * is what makes a valuation reproducible: last month's figure can still be read back exactly as it
 * was certified, because the record that replaced it is dated and can be left out.
 *
 * @param id the record's key
 * @param packageId the package it is booked against
 * @param code the bill item
 * @param stepId the step of the rule of credit it measures
 * @param extent the stretch measured
 * @param quantity what was measured
 * @param measuredOn the day it was measured
 * @param bookedBy who booked it
 * @param method how it was measured
 * @param supersedes the record this one replaces, if it is a remeasure
 * @param note anything the surveyor added
 */
public record SurveyRecord(
        String id,
        String packageId,
        ItemCode code,
        String stepId,
        ChainageRange extent,
        Quantity quantity,
        LocalDate measuredOn,
        String bookedBy,
        MeasurementMethod method,
        Optional<String> supersedes,
        String note) {

    public SurveyRecord {
        supersedes = supersedes == null ? Optional.empty() : supersedes;
    }

    /**
     * Build a record, refusing a blank id, a record that names nobody as booking it, and one that
     * supersedes itself.
     */
    public static SurveyRecord build(SurveyRecordInput input) {
        if (Text.trim(input.id()).isEmpty()) {
            throw new BadInputException("a survey record needs an id");
        }
        if (Text.trim(input.bookedBy()).isEmpty()) {
            throw new BadInputException("a survey record has to name who booked it", Details.of("record", input.id()));
        }
        if (input.id().equals(input.supersedes())) {
            throw new BadInputException("a survey record cannot supersede itself", Details.of("record", input.id()));
        }
        return new SurveyRecord(
                input.id(),
                input.packageId(),
                input.code(),
                input.stepId(),
                input.extent(),
                input.quantity(),
                input.measuredOn(),
                Text.trim(input.bookedBy()),
                input.method() == null ? MeasurementMethod.TAPE : input.method(),
                Optional.ofNullable(input.supersedes()),
                input.note() == null ? "" : input.note());
    }

    public SurveyRecord withExtent(ChainageRange other) {
        return new SurveyRecord(
                id, packageId, code, stepId, other, quantity, measuredOn, bookedBy, method, supersedes, note);
    }

    public SurveyRecord withQuantity(Quantity other) {
        return new SurveyRecord(
                id, packageId, code, stepId, extent, other, measuredOn, bookedBy, method, supersedes, note);
    }

    public SurveyRecord withPackage(String other) {
        return new SurveyRecord(
                id, other, code, stepId, extent, quantity, measuredOn, bookedBy, method, supersedes, note);
    }

    public boolean isRemeasure() {
        return supersedes.isPresent();
    }

    /** Whether this record is one the query picks out. */
    public boolean matches(RecordQuery query) {
        if (query.packageId().isPresent() && !packageId.equals(query.packageId().get())) {
            return false;
        }
        if (query.stepId().isPresent() && !stepId.equals(query.stepId().get())) {
            return false;
        }
        return query.code().isEmpty() || code.key().equals(query.code().get().key());
    }

    /** The records by id, refusing two that share one. */
    public static Map<String, SurveyRecord> index(List<SurveyRecord> records) {
        Map<String, SurveyRecord> map = new LinkedHashMap<>();
        for (SurveyRecord record : records) {
            if (map.containsKey(record.id())) {
                throw new BadInputException("two survey records share an id", Details.of("record", record.id()));
            }
            map.put(record.id(), record);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Walk back through the remeasures to the first booking. Throws on a loop, because a loop
     * makes every quantity on that chain unanswerable rather than merely wrong.
     */
    public static List<SurveyRecord> supersessionChain(List<SurveyRecord> records, String id) {
        Map<String, SurveyRecord> byId = index(records);
        List<SurveyRecord> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Optional<String> cursor = Optional.of(id);
        while (cursor.isPresent()) {
            String at = cursor.get();
            if (!seen.add(at)) {
                throw new ConflictException("the supersession chain loops", Details.of("record", at));
            }
            SurveyRecord record = byId.get(at);
            if (record == null) {
                break;
            }
            chain.add(record);
            cursor = record.supersedes();
        }
        return List.copyOf(chain);
    }

    /** Records that more than one remeasure claims to replace. */
    public static List<String> findDoubleSupersessions(List<SurveyRecord> records) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SurveyRecord record : records) {
            record.supersedes().ifPresent(id -> counts.merge(id, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * The records that count on a date.
     *
     * <p>A record measured after the date is not there yet, and, more subtly, a record superseded
     * by a remeasure that is itself after the date is still the live one. That second rule is what
     * stops a remeasure in May from changing what March certified.
     */
    public static List<SurveyRecord> effective(List<SurveyRecord> records, LocalDate asAt) {
        List<SurveyRecord> inDate =
                records.stream().filter(r -> !r.measuredOn().isAfter(asAt)).toList();
        Set<String> supersededIds = new HashSet<>();
        inDate.forEach(r -> r.supersedes().ifPresent(supersededIds::add));
        return inDate.stream().filter(r -> !supersededIds.contains(r.id())).toList();
    }

    /** The effective records the query picks out, in the order they were measured. */
    public static List<SurveyRecord> select(List<SurveyRecord> records, LocalDate asAt, RecordQuery query) {
        List<SurveyRecord> out = new ArrayList<>(
                effective(records, asAt).stream().filter(r -> r.matches(query)).toList());
        out.sort((a, b) -> {
            int byDate = a.measuredOn().compareTo(b.measuredOn());
            if (byDate != 0) {
                return byDate;
            }
            return a.id().compareTo(b.id()) < 0 ? -1 : 1;
        });
        return List.copyOf(out);
    }

    /** Every effective record, in the order they were measured. */
    public static List<SurveyRecord> select(List<SurveyRecord> records, LocalDate asAt) {
        return select(records, asAt, RecordQuery.ALL);
    }

    /** Everything booked against a query, added up. Absent when nothing matched. */
    public static Optional<Quantity> measuredQuantity(List<SurveyRecord> records, LocalDate asAt, RecordQuery query) {
        List<SurveyRecord> selected = select(records, asAt, query);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Quantity.sum(
                selected.stream().map(SurveyRecord::quantity).toList(),
                selected.get(0).quantity().unit()));
    }
}
