package io.github.rkaushikdev.chainageworks.domain.daywork;

import io.github.rkaushikdev.chainageworks.domain.money.Money;

/**
 * Materials used on daywork, at what they cost.
 *
 * @param description what they were
 * @param value what they cost before the addition
 */
public record MaterialEntry(String description, Money value) {}
