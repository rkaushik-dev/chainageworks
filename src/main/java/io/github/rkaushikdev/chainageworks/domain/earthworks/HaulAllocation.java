package io.github.rkaushikdev.chainageworks.domain.earthworks;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A haul plan: which material goes where, what it costs, and what is left over at each end.
 *
 * @param movements the loads, cheapest first
 * @param cost what they cost together
 * @param unplaced source volume nothing was found to do with
 * @param unfilled fill nothing was found for. Spare tip capacity is deliberately not counted here:
 *     a tip with room left is a tip that was not needed, which is a good outcome, and a job that
 *     reported it as a shortfall would read as failing every time it went well
 * @param spareTipCapacity tip capacity left unused, which is spare room rather than a problem
 */
public record HaulAllocation(
        List<Movement> movements, Money cost, Quantity unplaced, Quantity unfilled, Quantity spareTipCapacity) {

    public HaulAllocation {
        movements = List.copyOf(movements);
    }

    /**
     * What a haul plan is worked out from.
     *
     * @param sources where material comes from
     * @param destinations where it can go
     * @param links the haul roads between them
     * @param haulRate cost of moving a cubic metre one kilometre beyond the free haul
     * @param freeHaulKm how far material is moved before haulage is paid for at all; the rates for
     *     excavation and filling already carry the first stretch of every haul
     */
    public record Input(
            List<HaulSite> sources,
            List<HaulSite> destinations,
            List<HaulLink> links,
            Money haulRate,
            double freeHaulKm) {

        public Input {
            sources = List.copyOf(sources);
            destinations = List.copyOf(destinations);
            links = List.copyOf(links);
            if (!Double.isFinite(freeHaulKm) || freeHaulKm < 0) {
                throw new BadInputException(
                        "a free haul is a distance of nothing or more", Details.of("freeHaulKm", freeHaulKm));
            }
        }

        /** A plan where every kilometre of haul is paid for. */
        public Input(List<HaulSite> sources, List<HaulSite> destinations, List<HaulLink> links, Money haulRate) {
            this(sources, destinations, links, haulRate, 0);
        }

        /** The kilometres of a link that are paid for: whatever lies beyond the free haul. */
        public double overhaulKm(HaulLink link) {
            return Math.max(link.km() - freeHaulKm, 0);
        }
    }

    private record Priced(HaulLink link, HaulSite source, HaulSite destination, double rate) {}

    private static Quantity cubicMetres(long milli) {
        return Quantity.ofMilli(milli, Unit.M3);
    }

    /**
     * Match what has to be moved to where it can go, cheapest first.
     *
     * <p>Greedy on the total cost of a cubic metre rather than on the haul alone, which is the
     * point. A tip two kilometres away that charges to take material loses to a fill eight
     * kilometres away that does not, and an allocation that sorted on distance would pick the tip
     * every time and be wrong every time. Inside the free haul the distance costs nothing extra, so
     * two fills within it are told apart by distance only when everything else is equal.
     *
     * <p>Greedy rather than optimal on purpose. The real constraint on a haul plan is that the
     * answer has to be explainable to a foreman at seven in the morning, and a linear program that
     * saves two percent and cannot be explained is not worth the two percent.
     */
    public static HaulAllocation allocate(Input input) {
        Currency currency = input.haulRate().currency();
        checkSites(input);
        Map<String, Long> left = volumes(input.sources());
        Map<String, Long> wanted = volumes(input.destinations());
        List<Movement> movements = new ArrayList<>();
        for (Priced entry : priced(input)) {
            long available = left.getOrDefault(entry.source().id(), 0L);
            long room = wanted.getOrDefault(entry.destination().id(), 0L);
            long moved = Math.min(available, room);
            if (moved <= 0) {
                continue;
            }
            left.put(entry.source().id(), available - moved);
            wanted.put(entry.destination().id(), room - moved);
            movements.add(movement(input, entry, cubicMetres(moved), currency));
        }
        return new HaulAllocation(
                movements,
                Money.sum(movements.stream().map(Movement::cost).toList(), currency),
                Quantity.sum(
                        left.values().stream().map(HaulAllocation::cubicMetres).toList(), Unit.M3),
                remaining(input.destinations(), wanted, false),
                remaining(input.destinations(), wanted, true));
    }

    private static void checkSites(Input input) {
        List<HaulSite> everySite = new ArrayList<>(input.sources());
        everySite.addAll(input.destinations());
        for (HaulSite site : everySite) {
            if (site.volume().unit() != Unit.M3) {
                throw new BadInputException(
                        "a haul site is measured in cubic metres",
                        Details.of(
                                "site", site.id(), "unit", site.volume().unit().code()));
            }
            if (site.volume().milli() < 0) {
                throw new BadInputException("a haul site cannot hold a negative volume", Details.of("site", site.id()));
            }
        }
    }

    private static Map<String, Long> volumes(List<HaulSite> sites) {
        Map<String, Long> out = new LinkedHashMap<>();
        sites.forEach(site -> out.put(site.id(), site.volume().milli()));
        return out;
    }

    /** Every usable link with what a cubic metre costs to win, move and place along it, cheapest first. */
    private static List<Priced> priced(Input input) {
        Map<String, HaulSite> sources = new LinkedHashMap<>();
        input.sources().forEach(site -> sources.put(site.id(), site));
        Map<String, HaulSite> destinations = new LinkedHashMap<>();
        input.destinations().forEach(site -> destinations.put(site.id(), site));
        List<Priced> priced = new ArrayList<>();
        for (HaulLink link : input.links()) {
            HaulSite source = sources.get(link.fromId());
            HaulSite destination = destinations.get(link.toId());
            if (source == null || destination == null) {
                continue;
            }
            // Nobody buys material in order to throw it away. A link from a borrow pit to a tip is
            // always a mistake in the data, and matching supply to demand without this rule will
            // happily take it when there is surplus at one end and room at the other.
            if (source.kind() == SiteKind.BORROW && destination.kind() == SiteKind.TIP) {
                continue;
            }
            double rate = input.overhaulKm(link) * input.haulRate().minor()
                    + source.unitCost().minor()
                    + destination.unitCost().minor();
            priced.add(new Priced(link, source, destination, rate));
        }
        priced.sort((a, b) -> {
            int byRate = Double.compare(a.rate(), b.rate());
            return byRate != 0 ? byRate : Double.compare(a.link().km(), b.link().km());
        });
        return priced;
    }

    private static Movement movement(Input input, Priced entry, Quantity volume, Currency currency) {
        double amount = volume.amount();
        Money haulCost = input.haulRate().scale(amount * input.overhaulKm(entry.link()));
        Money siteCost = Money.of(
                        entry.source().unitCost().minor()
                                + entry.destination().unitCost().minor(),
                        currency)
                .scale(amount);
        return new Movement(
                entry.source().id(),
                entry.destination().id(),
                volume,
                entry.link().km(),
                haulCost,
                siteCost,
                Money.of(haulCost.minor() + siteCost.minor(), currency));
    }

    /** What is left to fill at the destinations, or the spare room at the tips. */
    private static Quantity remaining(List<HaulSite> destinations, Map<String, Long> wanted, boolean tips) {
        return Quantity.sum(
                destinations.stream()
                        .filter(site -> (site.kind() == SiteKind.TIP) == tips)
                        .map(site -> cubicMetres(wanted.getOrDefault(site.id(), 0L)))
                        .toList(),
                Unit.M3);
    }

    /** A plan that moves nothing and costs nothing. */
    public static HaulAllocation empty(Currency currency) {
        return new HaulAllocation(List.of(), Money.zero(currency), cubicMetres(0), cubicMetres(0), cubicMetres(0));
    }

    /** Movements out of a named site, which is what a haul road plan needs. */
    public List<Movement> movementsFrom(String siteId) {
        return movements.stream().filter(m -> m.fromId().equals(siteId)).toList();
    }

    public List<Movement> movementsTo(String siteId) {
        return movements.stream().filter(m -> m.toId().equals(siteId)).toList();
    }

    /** The haul moment, in cubic metre kilometres, which is what plant is sized on. */
    public double haulMoment() {
        double total = 0;
        for (Movement movement : movements) {
            total += movement.volume().amount() * movement.km();
        }
        return total;
    }

    public boolean isBalanced() {
        return unplaced.milli() == 0 && unfilled.milli() == 0;
    }
}
