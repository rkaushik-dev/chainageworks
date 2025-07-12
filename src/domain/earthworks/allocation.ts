import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { scaleMoney, sumMoney, zeroMoney } from '../money/money.js';
import type { Quantity } from '../quantity/quantity.js';
import { quantityAmount, quantityFromMilli, sumQuantities } from '../quantity/quantity.js';

/**
 * Somewhere material comes from, or goes to.
 *
 * A cut is a source that costs nothing to win because the job has to dig it
 * anyway. A borrow pit is a source that has to be bought. A tip is a
 * destination that has to be paid for. Keeping the three apart is what lets
 * the allocation prefer a long haul from a cut over a short haul from a borrow
 * pit, which is nearly always the cheaper answer and never the obvious one.
 */
export type SiteKind = 'cut' | 'borrow' | 'fill' | 'tip';

export interface HaulSite {
  readonly id: string;
  readonly name: string;
  readonly kind: SiteKind;
  /** Cubic metres available at a source, or wanted at a destination. */
  readonly volume: Quantity;
  /** Cost a cubic metre to win at a borrow pit or to accept at a tip. */
  readonly unitCost: Money;
}

export interface HaulLink {
  readonly fromId: string;
  readonly toId: string;
  /** One way haul distance in kilometres. */
  readonly km: number;
}

export interface AllocationInput {
  readonly sources: readonly HaulSite[];
  readonly destinations: readonly HaulSite[];
  readonly links: readonly HaulLink[];
  /** Cost of moving a cubic metre one kilometre. */
  readonly haulRate: Money;
}

export interface Movement {
  readonly fromId: string;
  readonly toId: string;
  readonly volume: Quantity;
  readonly km: number;
  readonly haulCost: Money;
  /** What the material cost to win, or what the tip charged to take it. */
  readonly siteCost: Money;
  readonly cost: Money;
}

export interface Allocation {
  readonly movements: readonly Movement[];
  readonly cost: Money;
  /** Source volume nothing was found to do with. */
  readonly unplaced: Quantity;
  /**
   * Fill nothing was found for. Spare tip capacity is deliberately not counted
   * here: a tip with room left is a tip that was not needed, which is a good
   * outcome, and a job that reported it as a shortfall would read as failing
   * every time it went well.
   */
  readonly unfilled: Quantity;
  /** Tip capacity left unused, which is spare room rather than a problem. */
  readonly spareTipCapacity: Quantity;
}

function cubicMetres(milli: number): Quantity {
  return quantityFromMilli(milli, 'm3');
}

function costOf(
  input: AllocationInput,
  source: HaulSite,
  destination: HaulSite,
  km: number,
): number {
  return km * input.haulRate.minor + source.unitCost.minor + destination.unitCost.minor;
}

/**
 * Match what has to be moved to where it can go, cheapest first.
 *
 * Greedy on the total cost of a cubic metre rather than on the haul alone,
 * which is the point. A tip two kilometres away that charges to take material
 * loses to a fill eight kilometres away that does not, and an allocation that
 * sorted on distance would pick the tip every time and be wrong every time.
 *
 * Greedy rather than optimal on purpose. The real constraint on a haul plan is
 * that the answer has to be explainable to a foreman at seven in the morning,
 * and a linear program that saves two percent and cannot be explained is not
 * worth the two percent.
 */
export function allocateHaul(input: AllocationInput): Allocation {
  const currency = input.haulRate.currency;
  for (const site of [...input.sources, ...input.destinations]) {
    if (site.volume.unit !== 'm3') {
      throw new BadInputError('a haul site is measured in cubic metres', {
        site: site.id,
        unit: site.volume.unit,
      });
    }
    if (site.volume.milli < 0) {
      throw new BadInputError('a haul site cannot hold a negative volume', { site: site.id });
    }
  }

  const left = new Map(input.sources.map((s) => [s.id, s.volume.milli]));
  const wanted = new Map(input.destinations.map((s) => [s.id, s.volume.milli]));
  const sourceById = new Map(input.sources.map((s) => [s.id, s]));
  const destinationById = new Map(input.destinations.map((s) => [s.id, s]));

  const priced = input.links
    .map((link) => {
      const source = sourceById.get(link.fromId);
      const destination = destinationById.get(link.toId);
      if (source === undefined || destination === undefined) return null;
      // Nobody buys material in order to throw it away. A link from a borrow
      // pit to a tip is always a mistake in the data, and matching supply to
      // demand without this rule will happily take it when there is surplus
      // at one end and room at the other.
      if (source.kind === 'borrow' && destination.kind === 'tip') return null;
      return { link, source, destination, rate: costOf(input, source, destination, link.km) };
    })
    .filter((entry): entry is NonNullable<typeof entry> => entry !== null)
    .sort((a, b) => a.rate - b.rate || a.link.km - b.link.km);

  const movements: Movement[] = [];
  for (const entry of priced) {
    const available = left.get(entry.source.id) ?? 0;
    const room = wanted.get(entry.destination.id) ?? 0;
    const moved = Math.min(available, room);
    if (moved <= 0) continue;

    left.set(entry.source.id, available - moved);
    wanted.set(entry.destination.id, room - moved);

    const volume = cubicMetres(moved);
    const amount = quantityAmount(volume);
    const haulCost = scaleMoney(input.haulRate, amount * entry.link.km);
    const siteCost = scaleMoney(
      { minor: entry.source.unitCost.minor + entry.destination.unitCost.minor, currency },
      amount,
    );
    movements.push({
      fromId: entry.source.id,
      toId: entry.destination.id,
      volume,
      km: entry.link.km,
      haulCost,
      siteCost,
      cost: { minor: haulCost.minor + siteCost.minor, currency },
    });
  }

  return {
    movements,
    cost: sumMoney(
      movements.map((m) => m.cost),
      currency,
    ),
    unplaced: sumQuantities([...left.values()].map(cubicMetres), 'm3'),
    unfilled: sumQuantities(
      input.destinations
        .filter((site) => site.kind !== 'tip')
        .map((site) => cubicMetres(wanted.get(site.id) ?? 0)),
      'm3',
    ),
    spareTipCapacity: sumQuantities(
      input.destinations
        .filter((site) => site.kind === 'tip')
        .map((site) => cubicMetres(wanted.get(site.id) ?? 0)),
      'm3',
    ),
  };
}

/** Movements out of a named site, which is what a haul road plan needs. */
export function movementsFrom(allocation: Allocation, siteId: string): Movement[] {
  return allocation.movements.filter((movement) => movement.fromId === siteId);
}

export function movementsTo(allocation: Allocation, siteId: string): Movement[] {
  return allocation.movements.filter((movement) => movement.toId === siteId);
}

/** The haul moment, in cubic metre kilometres, which is what plant is sized on. */
export function haulMoment(allocation: Allocation): number {
  return allocation.movements.reduce(
    (total, movement) => total + quantityAmount(movement.volume) * movement.km,
    0,
  );
}

export function isBalanced(allocation: Allocation): boolean {
  return allocation.unplaced.milli === 0 && allocation.unfilled.milli === 0;
}

export function emptyAllocation(currency: Money['currency']): Allocation {
  return {
    movements: [],
    cost: zeroMoney(currency),
    unplaced: cubicMetres(0),
    unfilled: cubicMetres(0),
    spareTipCapacity: cubicMetres(0),
  };
}

export function describeMovement(movement: Movement): string {
  return (
    `${String(quantityAmount(movement.volume))} m3 from ${movement.fromId} ` +
    `to ${movement.toId}, ${String(movement.km)} km`
  );
}
