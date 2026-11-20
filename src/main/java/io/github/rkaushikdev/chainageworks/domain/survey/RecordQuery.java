package io.github.rkaushikdev.chainageworks.domain.survey;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import java.util.Optional;

/**
 * Which survey records to pick out. An absent part matches everything.
 *
 * @param packageId only records booked against this package
 * @param code only records against this bill item
 * @param stepId only records against this step of the rule of credit
 */
public record RecordQuery(Optional<String> packageId, Optional<ItemCode> code, Optional<String> stepId) {

    /** Every record. */
    public static final RecordQuery ALL = new RecordQuery(Optional.empty(), Optional.empty(), Optional.empty());

    public RecordQuery {
        packageId = packageId == null ? Optional.empty() : packageId;
        code = code == null ? Optional.empty() : code;
        stepId = stepId == null ? Optional.empty() : stepId;
    }

    public RecordQuery withPackage(String id) {
        return new RecordQuery(Optional.of(id), code, stepId);
    }

    public RecordQuery withCode(ItemCode item) {
        return new RecordQuery(packageId, Optional.of(item), stepId);
    }

    public RecordQuery withStep(String id) {
        return new RecordQuery(packageId, code, Optional.of(id));
    }
}
