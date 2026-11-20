package io.github.rkaushikdev.chainageworks.domain.packages;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;

/**
 * How much of a bill item a package is responsible for.
 *
 * <p>The bill is written for the whole job and the works are built in stretches, so the two have
 * to be reconciled somewhere. This is that somewhere: a package takes a share of a bill item, and
 * the shares across every package are what the bill quantity is checked against.
 *
 * @param code the bill item
 * @param quantity the share of it this package carries
 */
public record PackageAllocation(ItemCode code, Quantity quantity) {}
