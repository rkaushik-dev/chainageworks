package io.github.rkaushikdev.chainageworks.domain.earthworks;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.util.Numbers;

/**
 * One load of material moved from a source to a destination in a haul plan.
 *
 * @param fromId the source
 * @param toId the destination
 * @param volume how much
 * @param km how far, one way
 * @param haulCost what the haul costs
 * @param siteCost what the material cost to win, or what the tip charged to take it
 * @param cost the two together
 */
public record Movement(
        String fromId, String toId, Quantity volume, double km, Money haulCost, Money siteCost, Money cost) {

    /** {@code 5000 m3 from CUT-A to EMB-B, 2 km}. */
    public String describe() {
        return Numbers.format(volume.amount()) + " m3 from " + fromId + " to " + toId + ", " + Numbers.format(km)
                + " km";
    }
}
