package io.github.rkaushikdev.chainageworks.support;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.survey.MeasurementMethod;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecordInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.util.Arrays;
import java.util.List;

/** Survey records for tests. */
public final class SurveyFixtures {

    private SurveyFixtures() {}

    /**
     * One test booking. Package, booker, method and supersedes have defaults.
     *
     * @param id the record id
     * @param packageId the package, or null for {@code WP-01}
     * @param code the item code as written
     * @param stepId the step measured
     * @param from the first station as written
     * @param to the last station as written
     * @param amount the measured amount
     * @param unit its unit
     * @param on the day as written
     * @param by who booked it, or null for {@code J Foreman}
     * @param method how it was measured, or null for the default
     * @param supersedes the record it replaces, or null
     */
    public record RecordSpec(
            String id,
            String packageId,
            String code,
            String stepId,
            String from,
            String to,
            double amount,
            Unit unit,
            String on,
            String by,
            MeasurementMethod method,
            String supersedes) {

        public static RecordSpec of(
                String id, String code, String stepId, String from, String to, double amount, Unit unit, String on) {
            return new RecordSpec(id, null, code, stepId, from, to, amount, unit, on, null, null, null);
        }

        public RecordSpec inPackage(String other) {
            return new RecordSpec(id, other, code, stepId, from, to, amount, unit, on, by, method, supersedes);
        }

        public RecordSpec bookedBy(String other) {
            return new RecordSpec(id, packageId, code, stepId, from, to, amount, unit, on, other, method, supersedes);
        }

        public RecordSpec method(MeasurementMethod other) {
            return new RecordSpec(id, packageId, code, stepId, from, to, amount, unit, on, by, other, supersedes);
        }

        public RecordSpec supersedes(String other) {
            return new RecordSpec(id, packageId, code, stepId, from, to, amount, unit, on, by, method, other);
        }
    }

    public static SurveyRecord record(RecordSpec spec) {
        return SurveyRecord.build(new SurveyRecordInput(
                spec.id(),
                spec.packageId() == null ? "WP-01" : spec.packageId(),
                ItemCode.parse(spec.code()),
                spec.stepId(),
                ChainageRange.of(Stations.parse(spec.from()), Stations.parse(spec.to())),
                Quantity.of(spec.amount(), spec.unit()),
                SiteDates.parse(spec.on()),
                spec.by() == null ? "J Foreman" : spec.by(),
                spec.method(),
                spec.supersedes(),
                null));
    }

    public static List<SurveyRecord> records(RecordSpec... specs) {
        return Arrays.stream(specs).map(SurveyFixtures::record).toList();
    }

    public static List<SurveyRecord> records(List<RecordSpec> specs) {
        return specs.stream().map(SurveyFixtures::record).toList();
    }
}
