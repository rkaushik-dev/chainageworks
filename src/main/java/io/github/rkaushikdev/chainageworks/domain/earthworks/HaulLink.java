package io.github.rkaushikdev.chainageworks.domain.earthworks;

/**
 * A haul road between a source and a destination.
 *
 * @param fromId the source
 * @param toId the destination
 * @param km one way haul distance in kilometres
 */
public record HaulLink(String fromId, String toId, double km) {}
