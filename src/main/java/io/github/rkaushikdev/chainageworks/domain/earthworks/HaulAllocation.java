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
     * @param haulRate cost of moving a cubic metre one kilometre
     */
    public record Input(List<HaulSite> sources, List<HaulSite> destinations, List<HaulLink> links, Money haulRate) {

        public Input {
            sources = List.copyOf(sources);
            destinations = List.copyOf(destinations);
            links = List.copyOf(links);
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
     * every time and be wrong every time.
     *
     * <p>Greedy rather than optimal on purpose. The real constraint on a haul plan is that the
     * answer has to be explainable to a foreman at seven in the morning, and a linear program that
     * saves two percent and cannot be explained is not worth the two percent.
     */
    public static HaulAllocation allocate(Input input) {
        Currency currency = input.haulRate().currency();
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

        Map<String, Long> left = new LinkedHashMap<>();
        Map<String, HaulSite> sourceById = new LinkedHashMap<>();
        for (HaulSite source : input.sources()) {
            left.put(source.id(), source.volume().milli());
            sourceById.put(source.id(), source);
        }
        Map<String, Long> wanted = new LinkedHashMap<>();
        Map<String, HaulSite> destinationById = new LinkedHashMap<>();
        for (HaulSite destination : input.destinations()) {
            wanted.put(destination.id(), destination.volume().milli());
            destinationById.put(destination.id(), destination);
        }

        List<Priced> priced = new ArrayList<>();
        for (HaulLink link : input.links()) {
            HaulSite source = sourceById.get(link.fromId());
            HaulSite destination = destinationById.get(link.toId());
            if (source == null || destination == null) {
                continue;
            }
            // Nobody buys material in order to throw it away. A link from a borrow pit to a tip is
            // always a mistake in the data, and matching supply to demand without this rule will
            // happily take it when there is surplus at one end and room at the other.
            if (source.kind() == SiteKind.BORROW && destination.kind() == SiteKind.TIP) {
                continue;
            }
            double rate = link.km() * input.haulRate().minor()
                    + source.unitCost().minor()
                    + destination.unitCost().minor();
            priced.add(new Priced(link, source, destination, rate));
        }
        priced.sort((a, b) -> {
            int byRate = Double.compare(a.rate(), b.rate());
            return byRate != 0 ? byRate : Double.compare(a.link().km(), b.link().km());
        });

        List<Movement> movements = new ArrayList<>();
        for (Priced entry : priced) {
            long available = left.getOrDefault(entry.source().id(), 0L);
            long room = wanted.getOrDefault(entry.destination().id(), 0L);
            long moved = Math.min(available, room);
            if (moved <= 0) {
                continue;
            }
            left.put(entry.source().id(), available - moved);
            wanted.put(entry.destination().id(), room - moved);

            Quantity volume = cubicMetres(moved);
            double amount = volume.amount();
            Money haulCost = input.haulRate().scale(amount * entry.link().km());
            Money siteCost = Money.of(
                            entry.source().unitCost().minor()
                                    + entry.destination().unitCost().minor(),
                            currency)
                    .scale(amount);
            movements.add(new Movement(
                    entry.source().id(),
                    entry.destination().id(),
                    volume,
                    entry.link().km(),
                    haulCost,
                    siteCost,
                    Money.of(haulCost.minor() + siteCost.minor(), currency)));
        }

        return new HaulAllocation(
                movements,
                Money.sum(movements.stream().map(Movement::cost).toList(), currency),
                Quantity.sum(
                        left.values().stream().map(HaulAllocation::cubicMetres).toList(), Unit.M3),
                Quantity.sum(
                        input.destinations().stream()
                                .filter(site -> site.kind() != SiteKind.TIP)
                                .map(site -> cubicMetres(wanted.getOrDefault(site.id(), 0L)))
                                .toList(),
                        Unit.M3),
                Quantity.sum(
                        input.destinations().stream()
                                .filter(site -> site.kind() == SiteKind.TIP)
                                .map(site -> cubicMetres(wanted.getOrDefault(site.id(), 0L)))
                                .toList(),
                        Unit.M3));
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
