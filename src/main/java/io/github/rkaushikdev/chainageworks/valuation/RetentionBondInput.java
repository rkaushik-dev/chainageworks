package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.time.LocalDate;

/**
 * What a retention bond is built from.
 *
 * @param id the bond
 * @param surety who stands behind it
 * @param maximum the most the bond can be called for
 * @param issuedOn the date it was issued
 * @param expiresOn the date it lapses
 * @param rate what the surety charges a year; null means {@link RetentionBond#DEFAULT_RATE}
 * @param reducesTo the reduced amount from substantial completion; null when it does not reduce
 */
public record RetentionBondInput(
        String id,
        String surety,
        Money maximum,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Percent rate,
        Money reducesTo) {

    public static RetentionBondInput of(
            String id, String surety, Money maximum, LocalDate issuedOn, LocalDate expiresOn) {
        return new RetentionBondInput(id, surety, maximum, issuedOn, expiresOn, null, null);
    }

    public RetentionBondInput withRate(Percent other) {
        return new RetentionBondInput(id, surety, maximum, issuedOn, expiresOn, other, reducesTo);
    }

    public RetentionBondInput withReducesTo(Money other) {
        return new RetentionBondInput(id, surety, maximum, issuedOn, expiresOn, rate, other);
    }
}
