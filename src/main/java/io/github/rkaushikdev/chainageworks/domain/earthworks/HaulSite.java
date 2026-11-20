package io.github.rkaushikdev.chainageworks.domain.earthworks;

import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;

/**
 * A source or destination of earthworks material.
 *
 * @param id the site's key
 * @param name what it is called
 * @param kind cut, borrow pit, fill or tip
 * @param volume cubic metres available at a source, or wanted at a destination
 * @param unitCost cost a cubic metre to win at a borrow pit or to accept at a tip
 */
public record HaulSite(String id, String name, SiteKind kind, Quantity volume, Money unitCost) {

    public HaulSite withVolume(Quantity other) {
        return new HaulSite(id, name, kind, other, unitCost);
    }
}
