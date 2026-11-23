package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import java.time.LocalDate;

/**
 * What a lot of materials on site is built from.
 *
 * @param id the lot
 * @param packageId the package it is for
 * @param code the bill item it will be built into
 * @param description what it is
 * @param quantity how much of it there is
 * @param value the invoice value
 * @param deliveredOn the day it arrived, or for a lot held off site the day it was set aside
 * @param vestedOn when title passed to the employer, or null when it has not
 * @param incorporatedOn when it was built in, or null while it is unfixed
 * @param protectedOnSite whether it is stored and protected, or null for yes
 * @param storedAt where a lot held off site is kept, or null when it is on site
 * @param bondReference the bond securing a lot held off site, or null when there is none
 */
public record MaterialsInput(
        String id,
        String packageId,
        ItemCode code,
        String description,
        Quantity quantity,
        Money value,
        LocalDate deliveredOn,
        LocalDate vestedOn,
        LocalDate incorporatedOn,
        Boolean protectedOnSite,
        String storedAt,
        String bondReference) {

    /** A protected lot on site that has neither vested nor been built in. */
    public static MaterialsInput of(
            String id,
            String packageId,
            ItemCode code,
            String description,
            Quantity quantity,
            Money value,
            LocalDate deliveredOn) {
        return new MaterialsInput(
                id, packageId, code, description, quantity, value, deliveredOn, null, null, null, null, null);
    }

    /** The input a lot was built from, to change and build again. */
    public static MaterialsInput from(MaterialsOnSite lot) {
        return new MaterialsInput(
                lot.id(),
                lot.packageId(),
                lot.code(),
                lot.description(),
                lot.quantity(),
                lot.value(),
                lot.deliveredOn(),
                lot.vestedOn().orElse(null),
                lot.incorporatedOn().orElse(null),
                lot.protectedOnSite(),
                lot.storedAt().orElse(null),
                lot.bondReference().orElse(null));
    }

    public MaterialsInput withId(String other) {
        Draft draft = new Draft(this);
        draft.id = other;
        return draft.input();
    }

    public MaterialsInput withDescription(String other) {
        Draft draft = new Draft(this);
        draft.description = other;
        return draft.input();
    }

    public MaterialsInput withQuantity(Quantity other) {
        Draft draft = new Draft(this);
        draft.quantity = other;
        return draft.input();
    }

    public MaterialsInput withValue(Money other) {
        Draft draft = new Draft(this);
        draft.value = other;
        return draft.input();
    }

    public MaterialsInput withDeliveredOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.deliveredOn = other;
        return draft.input();
    }

    public MaterialsInput withVestedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.vestedOn = other;
        return draft.input();
    }

    public MaterialsInput withIncorporatedOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.incorporatedOn = other;
        return draft.input();
    }

    public MaterialsInput withProtectedOnSite(boolean other) {
        Draft draft = new Draft(this);
        draft.protectedOnSite = other;
        return draft.input();
    }

    /** The lot as one held off site at {@code where}, secured by the bond {@code bond} if it has one. */
    public MaterialsInput withStoredOffSite(String where, String bond) {
        Draft draft = new Draft(this);
        draft.storedAt = where;
        draft.bondReference = bond;
        return draft.input();
    }

    /** A copy of the input with some fields changed, so each wither names only its own fields. */
    private static final class Draft {
        private String id;
        private String packageId;
        private ItemCode code;
        private String description;
        private Quantity quantity;
        private Money value;
        private LocalDate deliveredOn;
        private LocalDate vestedOn;
        private LocalDate incorporatedOn;
        private Boolean protectedOnSite;
        private String storedAt;
        private String bondReference;

        private Draft(MaterialsInput input) {
            id = input.id;
            packageId = input.packageId;
            code = input.code;
            description = input.description;
            quantity = input.quantity;
            value = input.value;
            deliveredOn = input.deliveredOn;
            vestedOn = input.vestedOn;
            incorporatedOn = input.incorporatedOn;
            protectedOnSite = input.protectedOnSite;
            storedAt = input.storedAt;
            bondReference = input.bondReference;
        }

        private MaterialsInput input() {
            return new MaterialsInput(
                    id,
                    packageId,
                    code,
                    description,
                    quantity,
                    value,
                    deliveredOn,
                    vestedOn,
                    incorporatedOn,
                    protectedOnSite,
                    storedAt,
                    bondReference);
        }
    }
}
