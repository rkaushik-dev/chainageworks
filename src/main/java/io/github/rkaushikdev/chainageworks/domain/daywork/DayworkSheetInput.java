package io.github.rkaushikdev.chainageworks.domain.daywork;

import java.time.LocalDate;
import java.util.List;

/**
 * What a daywork sheet is built from. A {@code null} signature date means unsigned; {@code null}
 * lists mean none.
 *
 * @param id the sheet's key
 * @param reference the sheet number on the pad
 * @param packageId the package the work was in
 * @param instruction the instruction this work was done under
 * @param workedOn the day the work was done
 * @param signedOn the day the engineer signed it
 * @param signedBy who signed it
 * @param labour the gangs
 * @param plant the plant
 * @param materials the materials
 */
public record DayworkSheetInput(
        String id,
        String reference,
        String packageId,
        String instruction,
        LocalDate workedOn,
        LocalDate signedOn,
        String signedBy,
        List<LabourEntry> labour,
        List<PlantEntry> plant,
        List<MaterialEntry> materials) {

    public DayworkSheetInput {
        labour = labour == null ? List.of() : List.copyOf(labour);
        plant = plant == null ? List.of() : List.copyOf(plant);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public DayworkSheetInput withId(String other) {
        return new DayworkSheetInput(
                other, reference, packageId, instruction, workedOn, signedOn, signedBy, labour, plant, materials);
    }

    public DayworkSheetInput withReference(String other) {
        return new DayworkSheetInput(
                id, other, packageId, instruction, workedOn, signedOn, signedBy, labour, plant, materials);
    }

    public DayworkSheetInput withWorkedOn(LocalDate other) {
        return new DayworkSheetInput(
                id, reference, packageId, instruction, other, signedOn, signedBy, labour, plant, materials);
    }

    /** The same sheet signed on {@code on} by {@code by}; a null date leaves it unsigned. */
    public DayworkSheetInput withSignature(LocalDate on, String by) {
        return new DayworkSheetInput(id, reference, packageId, instruction, workedOn, on, by, labour, plant, materials);
    }

    public DayworkSheetInput withSignedOn(LocalDate other) {
        return new DayworkSheetInput(
                id, reference, packageId, instruction, workedOn, other, signedBy, labour, plant, materials);
    }

    public DayworkSheetInput withSignedBy(String other) {
        return new DayworkSheetInput(
                id, reference, packageId, instruction, workedOn, signedOn, other, labour, plant, materials);
    }

    public DayworkSheetInput withLabour(List<LabourEntry> other) {
        return new DayworkSheetInput(
                id, reference, packageId, instruction, workedOn, signedOn, signedBy, other, plant, materials);
    }

    public DayworkSheetInput withPlant(List<PlantEntry> other) {
        return new DayworkSheetInput(
                id, reference, packageId, instruction, workedOn, signedOn, signedBy, labour, other, materials);
    }

    public DayworkSheetInput withMaterials(List<MaterialEntry> other) {
        return new DayworkSheetInput(
                id, reference, packageId, instruction, workedOn, signedOn, signedBy, labour, plant, other);
    }
}
