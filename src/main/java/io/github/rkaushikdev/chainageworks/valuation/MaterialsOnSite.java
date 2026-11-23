package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unfixed materials standing on site.
 *
 * <p>Four conditions have to hold before any of it can be certified, and the fourth is the one that
 * gets missed: the material has to still be unfixed. The moment it is built in, the measured work
 * carries its value, and leaving it in this list as well pays for it twice. Even a lot that meets
 * all four is only paid up to what its bill item has left to earn.
 *
 * @param id the lot
 * @param packageId the package it is for
 * @param code the bill item it will be built into
 * @param description what it is
 * @param quantity how much of it there is
 * @param value the invoice value, before any allowance is applied
 * @param deliveredOn the day it arrived
 * @param vestedOn when title passed to the employer, if it has
 * @param incorporatedOn when it stopped being unfixed material and became part of the works
 * @param protectedOnSite whether it is stored and protected
 */
public record MaterialsOnSite(
        String id,
        String packageId,
        ItemCode code,
        String description,
        Quantity quantity,
        Money value,
        LocalDate deliveredOn,
        Optional<LocalDate> vestedOn,
        Optional<LocalDate> incorporatedOn,
        boolean protectedOnSite) {

    /** What a contract usually allows against the invoice value. */
    public static final Percent DEFAULT_ALLOWANCE = Percent.of(90);

    /** What dropped out of the list between two cut-offs, and what joined it. */
    public record Movement(List<MaterialsOnSite> added, List<MaterialsOnSite> removed) {

        public Movement {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }
    }

    public static MaterialsOnSite build(MaterialsInput input) {
        if (input.value().minor() < 0) {
            throw new BadInputException(
                    "materials on site cannot carry a negative value", Details.of("materials", input.id()));
        }
        String description = Text.trim(input.description());
        if (description.isEmpty()) {
            throw new BadInputException("materials on site need a description", Details.of("materials", input.id()));
        }
        Optional<LocalDate> vestedOn = Optional.ofNullable(input.vestedOn());
        Optional<LocalDate> incorporatedOn = Optional.ofNullable(input.incorporatedOn());
        if (vestedOn.isPresent() && SiteDates.compare(vestedOn.get(), input.deliveredOn()) < 0) {
            throw new BadInputException(
                    "materials cannot vest before they were delivered",
                    Details.of(
                            "materials",
                            input.id(),
                            "delivered",
                            SiteDates.format(input.deliveredOn()),
                            "vested",
                            SiteDates.format(vestedOn.get())));
        }
        if (incorporatedOn.isPresent() && SiteDates.compare(incorporatedOn.get(), input.deliveredOn()) < 0) {
            throw new BadInputException(
                    "materials cannot be built in before they were delivered",
                    Details.of(
                            "materials",
                            input.id(),
                            "delivered",
                            SiteDates.format(input.deliveredOn()),
                            "incorporated",
                            SiteDates.format(incorporatedOn.get())));
        }
        return new MaterialsOnSite(
                input.id(),
                input.packageId(),
                input.code(),
                description,
                input.quantity(),
                input.value(),
                input.deliveredOn(),
                vestedOn,
                incorporatedOn,
                input.protectedOnSite() == null || input.protectedOnSite());
    }

    /**
     * Why this lot cannot be certified at a cut-off, if it cannot. The checks are in the order a
     * quantity surveyor walks them, and the first one that fails is the one reported.
     */
    public Optional<NotClaimableReason> reasonNotClaimable(LocalDate asAt) {
        if (SiteDates.compare(deliveredOn, asAt) > 0) {
            return Optional.of(NotClaimableReason.NOT_DELIVERED);
        }
        if (vestedOn.isEmpty() || SiteDates.compare(vestedOn.get(), asAt) > 0) {
            return Optional.of(NotClaimableReason.NOT_VESTED);
        }
        if (!protectedOnSite) {
            return Optional.of(NotClaimableReason.NOT_PROTECTED);
        }
        if (incorporatedOn.isPresent() && SiteDates.compare(incorporatedOn.get(), asAt) <= 0) {
            return Optional.of(NotClaimableReason.INCORPORATED);
        }
        return Optional.empty();
    }

    public boolean isClaimableAt(LocalDate asAt) {
        return reasonNotClaimable(asAt).isEmpty();
    }

    public static List<MaterialsOnSite> claimable(List<MaterialsOnSite> materials, LocalDate asAt) {
        return materials.stream().filter(m -> m.isClaimableAt(asAt)).toList();
    }

    /** {@link #value(List, LocalDate, Currency, Percent)} at the {@link #DEFAULT_ALLOWANCE}. */
    public static Money value(List<MaterialsOnSite> materials, LocalDate asAt, Currency currency) {
        return value(materials, asAt, currency, DEFAULT_ALLOWANCE);
    }

    /** The certified value of unfixed materials, after the contract allowance. */
    public static Money value(List<MaterialsOnSite> materials, LocalDate asAt, Currency currency, Percent allowance) {
        return claim(materials, asAt, currency, allowance, Map.of()).value();
    }

    /**
     * What the unfixed materials may be certified at.
     *
     * @param value the certified value after the allowance and the caps
     * @param lots the lots that could be certified at all
     * @param capped the bill items whose materials were held down to what the item has left to earn
     */
    public record Claim(Money value, List<MaterialsOnSite> lots, List<ItemCode> capped) {

        public Claim {
            lots = List.copyOf(lots);
            capped = List.copyOf(capped);
        }
    }

    /**
     * The certified value of unfixed materials, capped item by item at what each bill item still has
     * left to earn.
     *
     * <p>Materials are paid for in advance of the work they go into, never on top of it. A lot
     * worth more than the unearned balance of its item would have the employer paying for more
     * pipe than the bill will ever pay to lay, so every lot booked to one item is added up and
     * held down to that item's headroom.
     *
     * @param headroom what each item, by its {@link ItemCode#key()}, has left to earn; an item
     *     missing from the map is not capped
     */
    public static Claim claim(
            List<MaterialsOnSite> materials,
            LocalDate asAt,
            Currency currency,
            Percent allowance,
            Map<String, Money> headroom) {
        List<MaterialsOnSite> lots = claimable(materials, asAt);
        Map<String, Money> byItem = new LinkedHashMap<>();
        Map<String, ItemCode> codes = new LinkedHashMap<>();
        for (MaterialsOnSite lot : lots) {
            byItem.merge(lot.code().key(), allowance.of(lot.value()), Money::plus);
            codes.putIfAbsent(lot.code().key(), lot.code());
        }
        Money total = Money.zero(currency);
        List<ItemCode> capped = new ArrayList<>();
        for (Map.Entry<String, Money> item : byItem.entrySet()) {
            Money claimed = item.getValue();
            Money limit = headroom.get(item.getKey());
            if (limit != null && claimed.compareTo(limit) > 0) {
                claimed = Money.max(limit, Money.zero(currency));
                capped.add(codes.get(item.getKey()));
            }
            total = total.plus(claimed);
        }
        return new Claim(total, lots, capped);
    }

    /** What dropped out of the list between two cut-offs, and why. */
    public static Movement movement(List<MaterialsOnSite> materials, LocalDate from, LocalDate to) {
        Set<String> before =
                claimable(materials, from).stream().map(MaterialsOnSite::id).collect(Collectors.toSet());
        Set<String> after =
                claimable(materials, to).stream().map(MaterialsOnSite::id).collect(Collectors.toSet());
        return new Movement(
                materials.stream()
                        .filter(m -> after.contains(m.id()) && !before.contains(m.id()))
                        .toList(),
                materials.stream()
                        .filter(m -> before.contains(m.id()) && !after.contains(m.id()))
                        .toList());
    }

    /** {@code MOS-01 900 mm concrete pipe, delivered 2025-03-10}. */
    public String describe() {
        return id + " " + description + ", delivered " + SiteDates.format(deliveredOn);
    }
}
