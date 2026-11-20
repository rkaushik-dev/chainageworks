package io.github.rkaushikdev.chainageworks.domain.resource;

import java.time.LocalDate;
import java.util.List;

/**
 * What a resource return is built from. A {@code null} step means the whole package, a {@code
 * null} shift means the day shift, and {@code null} lists and note mean none.
 *
 * @param id the return's key
 * @param packageId the package the resources worked in
 * @param stepId the credit step they worked on
 * @param date the day
 * @param shift the shift
 * @param labour the gangs
 * @param plant the plant
 * @param returnedBy who filled it in
 * @param note anything the foreman added
 */
public record ResourceReturnInput(
        String id,
        String packageId,
        String stepId,
        LocalDate date,
        Shift shift,
        List<ResourceLine> labour,
        List<ResourceLine> plant,
        String returnedBy,
        String note) {

    public ResourceReturnInput {
        labour = labour == null ? List.of() : List.copyOf(labour);
        plant = plant == null ? List.of() : List.copyOf(plant);
    }

    /** A day shift return with nothing on it yet. */
    public static ResourceReturnInput of(String id, String packageId, LocalDate date, String returnedBy) {
        return new ResourceReturnInput(id, packageId, null, date, null, List.of(), List.of(), returnedBy, null);
    }

    public ResourceReturnInput withStep(String other) {
        return new ResourceReturnInput(id, packageId, other, date, shift, labour, plant, returnedBy, note);
    }

    public ResourceReturnInput withShift(Shift other) {
        return new ResourceReturnInput(id, packageId, stepId, date, other, labour, plant, returnedBy, note);
    }

    public ResourceReturnInput withLabour(List<ResourceLine> other) {
        return new ResourceReturnInput(id, packageId, stepId, date, shift, other, plant, returnedBy, note);
    }

    public ResourceReturnInput withPlant(List<ResourceLine> other) {
        return new ResourceReturnInput(id, packageId, stepId, date, shift, labour, other, returnedBy, note);
    }

    public ResourceReturnInput withNote(String other) {
        return new ResourceReturnInput(id, packageId, stepId, date, shift, labour, plant, returnedBy, other);
    }
}
