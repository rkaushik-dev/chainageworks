import { BadInputError } from '../../errors.js';
import type { ChainageRange } from '../chainage/range.js';
import type { Station } from '../chainage/station.js';
import { compareStations, formatStation } from '../chainage/station.js';
import type { Quantity } from '../quantity/quantity.js';
import { quantityFromMilli } from '../quantity/quantity.js';

/**
 * A cross section taken at a chainage.
 *
 * Areas are held as whole thousandths of a square metre, the same trick the
 * rest of the package uses: a section area comes off a survey to two or three
 * decimal places and a run of two hundred of them added as floats drifts by
 * enough to argue about.
 */
export interface CrossSection {
  readonly station: Station;
  /** Area of cut at this section, in thousandths of a square metre. */
  readonly cutArea: number;
  /** Area of fill at this section, in thousandths of a square metre. */
  readonly fillArea: number;
}

export interface Volumes {
  readonly cut: Quantity;
  readonly fill: Quantity;
}

export function crossSection(
  station: Station,
  cutArea: number,
  fillArea: number,
): CrossSection {
  for (const [name, value] of [
    ['cut area', cutArea],
    ['fill area', fillArea],
  ] as const) {
    if (!Number.isInteger(value) || value < 0) {
      throw new BadInputError(`a ${name} is a whole number of thousandths and not negative`, {
        value,
      });
    }
  }
  return { station, cutArea, fillArea };
}

/** Areas from square metres, rounded to the thousandth. */
export function sectionFromMetres(station: Station, cut: number, fill: number): CrossSection {
  const scale = (value: number) => Math.round(value * 1000);
  return crossSection(station, scale(cut), scale(fill));
}

export function sortSections(sections: readonly CrossSection[]): CrossSection[] {
  const sorted = [...sections].sort((a, b) => compareStations(a.station, b.station));
  for (let i = 1; i < sorted.length; i += 1) {
    if (sorted[i]!.station === sorted[i - 1]!.station) {
      throw new BadInputError('two cross sections were taken at the same chainage', {
        station: formatStation(sorted[i]!.station),
      });
    }
  }
  return sorted;
}

function volumeMilli(areaA: number, areaB: number, lengthMm: number): number {
  // Areas are thousandths of a square metre and the length is millimetres. The
  // two scales cancel to leave thousandths of a cubic metre, which is what a
  // quantity is held in, so the only divisor left is the two of the average.
  return Math.round(((areaA + areaB) * lengthMm) / 2000);
}

/**
 * Average end area, which is what a bill of quantities is measured on.
 *
 * It is not the exact volume of the solid between the two sections and it is
 * not meant to be. It is the method the specification names, so it is the
 * method the payment is worked out by, and using a better one would produce a
 * figure the engineer will not certify.
 */
export function endAreaVolume(a: CrossSection, b: CrossSection): Volumes {
  const length = Math.abs(b.station - a.station);
  return {
    cut: quantityFromMilli(volumeMilli(a.cutArea, b.cutArea, length), 'm3'),
    fill: quantityFromMilli(volumeMilli(a.fillArea, b.fillArea, length), 'm3'),
  };
}

/**
 * The prismoidal rule, for checking the end area figure.
 *
 * End area overstates a solid that swells in the middle and understates one
 * that pinches, and on a deep cutting the difference runs to a few percent.
 * The check is worth having even though the payment stays on end area.
 */
export function prismoidalVolume(
  a: CrossSection,
  middle: CrossSection,
  b: CrossSection,
): Volumes {
  const length = Math.abs(b.station - a.station);
  const rule = (areaA: number, areaMid: number, areaB: number): number =>
    Math.round(((areaA + 4 * areaMid + areaB) * length) / 6000);
  return {
    cut: quantityFromMilli(rule(a.cutArea, middle.cutArea, b.cutArea), 'm3'),
    fill: quantityFromMilli(rule(a.fillArea, middle.fillArea, b.fillArea), 'm3'),
  };
}

/** Every volume between consecutive sections, added. */
export function totalVolumes(sections: readonly CrossSection[]): Volumes {
  const sorted = sortSections(sections);
  let cut = 0;
  let fill = 0;
  for (let i = 1; i < sorted.length; i += 1) {
    const volume = endAreaVolume(sorted[i - 1]!, sorted[i]!);
    cut += volume.cut.milli;
    fill += volume.fill.milli;
  }
  return {
    cut: quantityFromMilli(cut, 'm3'),
    fill: quantityFromMilli(fill, 'm3'),
  };
}

/**
 * Volumes over a stretch, interpolating a section at each end.
 *
 * The interpolation matters because a package boundary almost never falls on a
 * surveyed section, and taking the nearest one instead moves a chainage worth
 * of earthworks from one package to the next.
 */
export function volumesOver(sections: readonly CrossSection[], extent: ChainageRange): Volumes {
  const sorted = sortSections(sections);
  const clipped: CrossSection[] = [];
  for (let i = 0; i < sorted.length; i += 1) {
    const here = sorted[i]!;
    if (here.station >= extent.from && here.station <= extent.to) clipped.push(here);
    const next = sorted[i + 1];
    if (next === undefined) continue;
    for (const edge of [extent.from, extent.to]) {
      if (here.station < edge && edge < next.station) {
        clipped.push(interpolate(here, next, edge));
      }
    }
  }
  return totalVolumes(clipped);
}

/** A section at a chainage between two surveyed ones, areas taken straight. */
export function interpolate(a: CrossSection, b: CrossSection, at: Station): CrossSection {
  const span = b.station - a.station;
  if (span === 0) return { ...a, station: at };
  const share = (at - a.station) / span;
  const between = (from: number, to: number) => Math.round(from + (to - from) * share);
  return crossSection(at, between(a.cutArea, b.cutArea), between(a.fillArea, b.fillArea));
}

/**
 * Fill needs more material out of the cut than it finishes up as, because it is
 * compacted. A bulking factor over one means the cut goes further than its own
 * volume suggests; under one, less.
 */
export function balanceAfterBulking(volumes: Volumes, bulkingFactor: number): Quantity {
  if (!Number.isFinite(bulkingFactor) || bulkingFactor <= 0) {
    throw new BadInputError('a bulking factor is a positive number', { bulkingFactor });
  }
  const usable = Math.round(volumes.cut.milli * bulkingFactor);
  return quantityFromMilli(usable - volumes.fill.milli, 'm3');
}

export function isBorrowNeeded(volumes: Volumes, bulkingFactor: number): boolean {
  return balanceAfterBulking(volumes, bulkingFactor).milli < 0;
}
