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
 * @param deliveredOn the day it arrived
 * @param vestedOn when title passed to the employer, or null when it has not
 * @param incorporatedOn when it was built in, or null while it is unfixed
 * @param protectedOnSite whether it is stored and protected, or null for yes
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
        Boolean protectedOnSite) {

    /** A protected lot that has neither vested nor been built in. */
    public static MaterialsInput of(
            String id,
            String packageId,
            ItemCode code,
            String description,
            Quantity quantity,
            Money value,
            LocalDate deliveredOn) {
        return new MaterialsInput(id, packageId, code, description, quantity, value, deliveredOn, null, null, null);
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
                lot.protectedOnSite());
    }

    public MaterialsInput withId(String other) {
        return new MaterialsInput(
                other,
                packageId,
                code,
                description,
                quantity,
                value,
                deliveredOn,
                vestedOn,
                incorporatedOn,
                protectedOnSite);
    }

    public MaterialsInput withDescription(String other) {
        return new MaterialsInput(
                id, packageId, code, other, quantity, value, deliveredOn, vestedOn, incorporatedOn, protectedOnSite);
    }

    public MaterialsInput withQuantity(Quantity other) {
        return new MaterialsInput(
                id, packageId, code, description, other, value, deliveredOn, vestedOn, incorporatedOn, protectedOnSite);
    }

    public MaterialsInput withValue(Money other) {
        return new MaterialsInput(
                id,
                packageId,
                code,
                description,
                quantity,
                other,
                deliveredOn,
                vestedOn,
                incorporatedOn,
                protectedOnSite);
    }

    public MaterialsInput withDeliveredOn(LocalDate other) {
        return new MaterialsInput(
                id, packageId, code, description, quantity, value, other, vestedOn, incorporatedOn, protectedOnSite);
    }

    public MaterialsInput withVestedOn(LocalDate other) {
        return new MaterialsInput(
                id, packageId, code, description, quantity, value, deliveredOn, other, incorporatedOn, protectedOnSite);
    }

    public MaterialsInput withIncorporatedOn(LocalDate other) {
        return new MaterialsInput(
                id, packageId, code, description, quantity, value, deliveredOn, vestedOn, other, protectedOnSite);
    }

    public MaterialsInput withProtectedOnSite(boolean other) {
        return new MaterialsInput(
                id, packageId, code, description, quantity, value, deliveredOn, vestedOn, incorporatedOn, other);
    }
}
