import { BadInputError } from '../../errors.js';
import type { Station } from '../chainage/station.js';
import { asStation, formatStation, MM_PER_METRE } from '../chainage/station.js';
import type { Quantity } from '../quantity/quantity.js';
import { quantityFromMilli } from '../quantity/quantity.js';
import type { CrossSection } from './crossSection.js';
import { endAreaVolume, sortSections } from './crossSection.js';

/**
 * One point on the mass haul diagram.
 *
 * The ordinate is the running total of cut less fill from the start of the
 * run. Where it rises there is surplus material to move forward; where it falls
 * there is a deficit to fill. Where it crosses back through a level it has
 * already been at, everything between the two crossings balances, and that is
 * the only thing on the diagram anybody actually reads off it.
 */
export interface HaulOrdinate {
  readonly station: Station;
  /** Cut less fill from the start, in thousandths of a cubic metre. */
  readonly ordinate: number;
}

export interface HaulRun {
  readonly ordinates: readonly HaulOrdinate[];
  readonly bulkingFactor: number;
}

/**
 * Build the diagram, applying the bulking factor to the cut.
 *
 * The factor goes on the cut rather than the fill because that is the direction
 * the material moves: a cubic metre dug out makes some other number of cubic
 * metres of compacted embankment, and pretending otherwise is what leaves a job
 * short of fill in its last month.
 */
export function buildHaulRun(sections: readonly CrossSection[], bulkingFactor = 1): HaulRun {
  if (!Number.isFinite(bulkingFactor) || bulkingFactor <= 0) {
    throw new BadInputError('a bulking factor is a positive number', { bulkingFactor });
  }
  const sorted = sortSections(sections);
  const first = sorted[0];
  if (first === undefined) return { ordinates: [], bulkingFactor };

  const ordinates: HaulOrdinate[] = [{ station: first.station, ordinate: 0 }];
  let running = 0;
  for (let i = 1; i < sorted.length; i += 1) {
    const volume = endAreaVolume(sorted[i - 1]!, sorted[i]!);
    running += Math.round(volume.cut.milli * bulkingFactor) - volume.fill.milli;
    ordinates.push({ station: sorted[i]!.station, ordinate: running });
  }
  return { ordinates, bulkingFactor };
}

export function closingOrdinate(run: HaulRun): Quantity {
  const last = run.ordinates[run.ordinates.length - 1];
  return quantityFromMilli(last?.ordinate ?? 0, 'm3');
}

/** Positive means surplus to dispose of, negative means fill to import. */
export function isSurplus(run: HaulRun): boolean {
  return closingOrdinate(run).milli > 0;
}

/**
 * Chainages where the diagram crosses back through zero.
 *
 * Between two of these the cut and the fill balance exactly, which is the
 * stretch a haul is planned over. The crossing is interpolated because the
 * balance point almost never falls on a surveyed section.
 */
export function balancePoints(run: HaulRun): Station[] {
  const out: Station[] = [];
  for (let i = 1; i < run.ordinates.length; i += 1) {
    const before = run.ordinates[i - 1]!;
    const here = run.ordinates[i]!;
    // The start is not a balance point. It is where the count began, and
    // reporting it puts a haul on the drawing that nobody is going to do.
    if (here.ordinate === 0) {
      out.push(here.station);
      continue;
    }
    if (
      (before.ordinate < 0 && here.ordinate > 0) ||
      (before.ordinate > 0 && here.ordinate < 0)
    ) {
      const span = here.station - before.station;
      const share = Math.abs(before.ordinate) / Math.abs(here.ordinate - before.ordinate);
      out.push(asStation(before.station + Math.round(span * share)));
    }
  }
  return out;
}

/**
 * The haul moment, in cubic metre metres.
 *
 * This is the area under the mass haul curve, and it is what a haul is priced
 * on: moving a lot of material a short way and a little a long way can come to
 * the same money, and only the moment says so.
 */
export function haulMoment(run: HaulRun): number {
  let moment = 0;
  for (let i = 1; i < run.ordinates.length; i += 1) {
    const before = run.ordinates[i - 1]!;
    const here = run.ordinates[i]!;
    const lengthMetres = (here.station - before.station) / MM_PER_METRE;
    const averageVolume = (before.ordinate + here.ordinate) / 2 / 1000;
    moment += Math.abs(averageVolume) * lengthMetres;
  }
  return Math.round(moment * 100) / 100;
}

export const DEFAULT_FREE_HAUL_METRES = 300;

export interface Overhaul {
  readonly freeHaulMetres: number;
  /** The moment inside the free haul, which costs nothing extra. */
  readonly freeMoment: number;
  /** The moment past it, which is what overhaul is paid on. */
  readonly overhaulMoment: number;
}

/**
 * Split the haul moment into what the contract gives away and what it pays for.
 *
 * The free haul distance is the length material may be moved at the excavation
 * rate. Past it the contractor is paid overhaul on the extra distance only, so
 * the split has to be made span by span rather than on the total.
 */
export function overhaul(
  run: HaulRun,
  freeHaulMetres: number = DEFAULT_FREE_HAUL_METRES,
): Overhaul {
  if (!Number.isFinite(freeHaulMetres) || freeHaulMetres < 0) {
    throw new BadInputError('a free haul distance is not negative', { freeHaulMetres });
  }
  let free = 0;
  let over = 0;
  for (let i = 1; i < run.ordinates.length; i += 1) {
    const before = run.ordinates[i - 1]!;
    const here = run.ordinates[i]!;
    const lengthMetres = (here.station - before.station) / MM_PER_METRE;
    const averageVolume = Math.abs((before.ordinate + here.ordinate) / 2 / 1000);
    const freeLength = Math.min(lengthMetres, freeHaulMetres);
    free += averageVolume * freeLength;
    over += averageVolume * (lengthMetres - freeLength);
  }
  return {
    freeHaulMetres,
    freeMoment: Math.round(free * 100) / 100,
    overhaulMoment: Math.round(over * 100) / 100,
  };
}

export function describeHaul(run: HaulRun): string {
  const closing = closingOrdinate(run);
  const state = closing.milli === 0 ? 'balanced' : isSurplus(run) ? 'surplus' : 'deficit';
  const points = balancePoints(run).map((s) => formatStation(s, { decimals: 0 }));
  return (
    `${state} of ${String(Math.abs(closing.milli) / 1000)} m3, ` +
    `${String(points.length)} balance points${points.length === 0 ? '' : ` at ${points.join(', ')}`}`
  );
}
