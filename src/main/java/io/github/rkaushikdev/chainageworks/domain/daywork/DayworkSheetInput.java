package io.github.rkaushikdev.chainageworks.domain.daywork;

import java.time.LocalDate;
import java.util.List;

/**
 * What a daywork sheet is built from. A {@code null} signature date means unsigned, a {@code null}
 * submission date means the sheet went in with its signature, and {@code null} lists mean none.
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
 * @param submittedOn the day the contractor handed the sheet in
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
        List<MaterialEntry> materials,
        LocalDate submittedOn) {

    public DayworkSheetInput {
        labour = labour == null ? List.of() : List.copyOf(labour);
        plant = plant == null ? List.of() : List.copyOf(plant);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    /** A sheet handed in with its signature. */
    public DayworkSheetInput(
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
        this(id, reference, packageId, instruction, workedOn, signedOn, signedBy, labour, plant, materials, null);
    }

    public DayworkSheetInput withId(String other) {
        Draft draft = new Draft(this);
        draft.id = other;
        return draft.input();
    }

    public DayworkSheetInput withReference(String other) {
        Draft draft = new Draft(this);
        draft.reference = other;
        return draft.input();
    }

    public DayworkSheetInput withWorkedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.workedOn = other;
        return draft.input();
    }

    public DayworkSheetInput withSignedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.signedOn = other;
        return draft.input();
    }

    public DayworkSheetInput withSignedBy(String other) {
        Draft draft = new Draft(this);
        draft.signedBy = other;
        return draft.input();
    }

    public DayworkSheetInput withLabour(List<LabourEntry> other) {
        Draft draft = new Draft(this);
        draft.labour = other;
        return draft.input();
    }

    public DayworkSheetInput withPlant(List<PlantEntry> other) {
        Draft draft = new Draft(this);
        draft.plant = other;
        return draft.input();
    }

    public DayworkSheetInput withMaterials(List<MaterialEntry> other) {
        Draft draft = new Draft(this);
        draft.materials = other;
        return draft.input();
    }

    public DayworkSheetInput withSubmittedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.submittedOn = other;
        return draft.input();
    }

    /** The same sheet signed on {@code on} by {@code by}; a null date leaves it unsigned. */
    public DayworkSheetInput withSignature(LocalDate on, String by) {
        Draft draft = new Draft(this);
        draft.signedOn = on;
        draft.signedBy = by;
        return draft.input();
    }

    /** A copy of the input with some fields changed, so each wither names only its own fields. */
    private static final class Draft {
        private String id;
        private String reference;
        private String packageId;
        private String instruction;
        private LocalDate workedOn;
        private LocalDate signedOn;
        private String signedBy;
        private List<LabourEntry> labour;
        private List<PlantEntry> plant;
        private List<MaterialEntry> materials;
        private LocalDate submittedOn;

        private Draft(DayworkSheetInput input) {
            id = input.id;
            reference = input.reference;
            packageId = input.packageId;
            instruction = input.instruction;
            workedOn = input.workedOn;
            signedOn = input.signedOn;
            signedBy = input.signedBy;
            labour = input.labour;
            plant = input.plant;
            materials = input.materials;
            submittedOn = input.submittedOn;
        }

        private DayworkSheetInput input() {
            return new DayworkSheetInput(
                    id,
                    reference,
                    packageId,
                    instruction,
                    workedOn,
                    signedOn,
                    signedBy,
                    labour,
                    plant,
                    materials,
                    submittedOn);
        }
    }
}
