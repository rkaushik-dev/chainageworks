package io.github.rkaushikdev.chainageworks.domain.delay;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.time.LocalDate;
import java.util.List;

/**
 * What a delay event is built from. A {@code null} extent, notice date or award means none.
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
        Integer awardedDays) {

    public DelayEventInput {
        packageIds = packageIds == null ? List.of() : List.copyOf(packageIds);
    }

    /** An event with no extent, no packages, no notice and no award. */
    public static DelayEventInput of(
            String id, String reference, DelayCause cause, String description, LocalDate from, LocalDate to) {
        return new DelayEventInput(id, reference, cause, description, from, to, null, List.of(), null, null);
    }

    public DelayEventInput withId(String other) {
        return new DelayEventInput(
                other, reference, cause, description, from, to, extent, packageIds, notifiedOn, awardedDays);
    }

    public DelayEventInput withCause(DelayCause other) {
        return new DelayEventInput(
                id, reference, other, description, from, to, extent, packageIds, notifiedOn, awardedDays);
    }

    public DelayEventInput withDescription(String other) {
        return new DelayEventInput(id, reference, cause, other, from, to, extent, packageIds, notifiedOn, awardedDays);
    }

    public DelayEventInput withPeriod(LocalDate start, LocalDate end) {
        return new DelayEventInput(
                id, reference, cause, description, start, end, extent, packageIds, notifiedOn, awardedDays);
    }

    public DelayEventInput withExtent(ChainageRange other) {
        return new DelayEventInput(
                id, reference, cause, description, from, to, other, packageIds, notifiedOn, awardedDays);
    }

    public DelayEventInput withPackages(List<String> other) {
        return new DelayEventInput(id, reference, cause, description, from, to, extent, other, notifiedOn, awardedDays);
    }

    public DelayEventInput withNotifiedOn(LocalDate other) {
        return new DelayEventInput(id, reference, cause, description, from, to, extent, packageIds, other, awardedDays);
    }

    public DelayEventInput withAwardedDays(Integer other) {
        return new DelayEventInput(id, reference, cause, description, from, to, extent, packageIds, notifiedOn, other);
    }
}
