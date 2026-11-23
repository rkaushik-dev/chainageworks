package io.github.rkaushikdev.chainageworks.domain.delay;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;
import java.util.List;

/**
 * What a delay event is built from. A {@code null} extent, notice date or award means none, and a
 * {@code null} critical flag means the event was on the critical path.
 *
 * @param id the event's key
 * @param reference the early warning or notice reference
 * @param cause why the works stopped
 * @param description what happened
 * @param from the first day of the delay
 * @param to the last day of the delay
 * @param extent where on the alignment, when the event has a place
 * @param packageIds the packages it held up
 * @param notifiedOn when the contractor gave notice
 * @param awardedDays working days awarded by the engineer
 * @param critical whether the work it held up was on the critical path
 */
public record DelayEventInput(
        String id,
        String reference,
        DelayCause cause,
        String description,
        LocalDate from,
        LocalDate to,
        ChainageRange extent,
        List<String> packageIds,
        LocalDate notifiedOn,
        Integer awardedDays,
        Boolean critical) {

    public DelayEventInput {
        packageIds = packageIds == null ? List.of() : List.copyOf(packageIds);
    }

    /** An event on the critical path with no extent, no packages, no notice and no award. */
    public static DelayEventInput of(
            String id, String reference, DelayCause cause, String description, LocalDate from, LocalDate to) {
        return new DelayEventInput(id, reference, cause, description, from, to, null, List.of(), null, null, null);
    }

    public DelayEventInput withId(String other) {
        Draft draft = new Draft(this);
        draft.id = other;
        return draft.input();
    }

    public DelayEventInput withCause(DelayCause other) {
        Draft draft = new Draft(this);
        draft.cause = other;
        return draft.input();
    }

    public DelayEventInput withDescription(String other) {
        Draft draft = new Draft(this);
        draft.description = other;
        return draft.input();
    }

    public DelayEventInput withExtent(ChainageRange other) {
        Draft draft = new Draft(this);
        draft.extent = other;
        return draft.input();
    }

    public DelayEventInput withPackages(List<String> other) {
        Draft draft = new Draft(this);
        draft.packageIds = other;
        return draft.input();
    }

    public DelayEventInput withNotifiedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.notifiedOn = other;
        return draft.input();
    }

    public DelayEventInput withAwardedDays(Integer other) {
        Draft draft = new Draft(this);
        draft.awardedDays = other;
        return draft.input();
    }

    public DelayEventInput withPeriod(LocalDate start, LocalDate end) {
        Draft draft = new Draft(this);
        draft.from = start;
        draft.to = end;
        return draft.input();
    }

    /** The event as one that held up critical work, or as one the programme float absorbed. */
    public DelayEventInput withCritical(boolean other) {
        Draft draft = new Draft(this);
        draft.critical = other;
        return draft.input();
    }

    /** A copy of the input with some fields changed, so each wither names only its own fields. */
    private static final class Draft {
        private String id;
        private String reference;
        private DelayCause cause;
        private String description;
        private LocalDate from;
        private LocalDate to;
        private ChainageRange extent;
        private List<String> packageIds;
        private LocalDate notifiedOn;
        private Integer awardedDays;
        private Boolean critical;

        private Draft(DelayEventInput input) {
            id = input.id;
            reference = input.reference;
            cause = input.cause;
            description = input.description;
            from = input.from;
            to = input.to;
            extent = input.extent;
            packageIds = input.packageIds;
            notifiedOn = input.notifiedOn;
            awardedDays = input.awardedDays;
            critical = input.critical;
        }

        private DelayEventInput input() {
            return new DelayEventInput(
                    id, reference, cause, description, from, to, extent, packageIds, notifiedOn, awardedDays, critical);
        }
    }
}
