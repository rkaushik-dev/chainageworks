package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;

/**
 * One index and the share of the works it stands for.
 *
 * <p>The shares are set at tender against the make up of the price, which is why they are contract
 * terms rather than anything measured. Nothing about how the work actually went changes them, and
 * the temptation to reweight them when the mix turns out differently is the temptation to reprice
 * the contract.
 *
 * @param indexId the index series
 * @param weight its share of the price
 */
public record Weighting(String indexId, Percent weight) {}
