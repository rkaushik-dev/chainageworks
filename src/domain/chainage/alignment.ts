import { BadInputError, ConflictError } from '../../errors.js';
import type { Station } from './station.js';
import { asStation, compareStations, formatStation } from './station.js';

/**
 * A point where the through chainage stops being continuous.
 *
 * When a route is realigned after the bill is written, nobody restations the
 * whole job. The designer writes an equation instead: the last station of the
 * old series equals the first station of the new one at the same physical
 * point. `12+340.000 back = 12+310.000 ahead` means the two labels name one
 * place, and thirty metres of chainage exist twice.
 */
export interface ChainageEquation {
  /** Last station of the outgoing series. */
  readonly back: Station;
  /** First station of the incoming series, at the same place on the ground. */
  readonly ahead: Station;
}

export type EquationKind = 'overlap' | 'gap' | 'continuous';

export interface Alignment {
  readonly id: string;
  readonly name: string;
  readonly start: Station;
  readonly end: Station;
  readonly equations: readonly ChainageEquation[];
}

/** One continuous run of stationing, with where it begins on the ground. */
export interface AlignmentSegment {
  /** Zero based series number. Series 0 is the original stationing. */
  readonly series: number;
  readonly from: Station;
  readonly to: Station;
  /** Millimetres along the ground from the start of the alignment. */
  readonly startDistance: number;
}

export interface AlignmentPosition {
  readonly series: number;
  readonly distance: number;
}

export function equationKind(equation: ChainageEquation): EquationKind {
  const delta = compareStations(equation.ahead, equation.back);
  if (delta < 0) return 'overlap';
  if (delta > 0) return 'gap';
  return 'continuous';
}

export function describeEquation(equation: ChainageEquation): string {
  return `${formatStation(equation.back)} back = ${formatStation(equation.ahead)} ahead`;
}

export interface AlignmentInput {
  readonly id: string;
  readonly name: string;
  readonly start: Station;
  readonly end: Station;
  readonly equations?: readonly ChainageEquation[];
}

export function buildAlignment(input: AlignmentInput): Alignment {
  const equations = input.equations ?? [];
  const alignment: Alignment = {
    id: input.id,
    name: input.name,
    start: input.start,
    end: input.end,
    equations,
  };
  // Building the segments is the validation: it is the only place that can tell
  // an equation that advances from one that folds the route back on itself.
  alignmentSegments(alignment);
  return alignment;
}

export function alignmentSegments(alignment: Alignment): AlignmentSegment[] {
  const segments: AlignmentSegment[] = [];
  let from = alignment.start;
  let distance = 0;
  for (const [index, equation] of alignment.equations.entries()) {
    if (compareStations(equation.back, from) <= 0) {
      throw new BadInputError('a chainage equation must sit ahead of the series it closes', {
        equation: describeEquation(equation),
        seriesStart: formatStation(from),
        index,
      });
    }
    const length = equation.back - from;
    segments.push({ series: index, from, to: equation.back, startDistance: distance });
    distance += length;
    from = equation.ahead;
  }
  if (compareStations(alignment.end, from) <= 0) {
    throw new BadInputError('the alignment ends at or before the start of its last series', {
      end: formatStation(alignment.end),
      seriesStart: formatStation(from),
    });
  }
  segments.push({
    series: alignment.equations.length,
    from,
    to: alignment.end,
    startDistance: distance,
  });
  return segments;
}

/** Real distance on the ground from one end to the other, in millimetres. */
export function alignmentLength(alignment: Alignment): number {
  const segments = alignmentSegments(alignment);
  const last = segments[segments.length - 1];
  if (last === undefined) return 0;
  return last.startDistance + (last.to - last.from);
}

/**
 * Every place a station label lands. Both ends of a segment count, so the two
 * labels either side of an equation resolve to the same distance and a
 * continuous equation is not reported as ambiguous.
 */
export function locateStation(alignment: Alignment, station: Station): AlignmentPosition[] {
  const out: AlignmentPosition[] = [];
  for (const segment of alignmentSegments(alignment)) {
    if (
      compareStations(station, segment.from) >= 0 &&
      compareStations(station, segment.to) <= 0
    ) {
      out.push({
        series: segment.series,
        distance: segment.startDistance + (station - segment.from),
      });
    }
  }
  return out;
}

export function isStationOnAlignment(alignment: Alignment, station: Station): boolean {
  return locateStation(alignment, station).length > 0;
}

/**
 * A station is ambiguous when an overlap equation makes the same label name two
 * different places. The caller has to say which series it meant.
 */
export function isStationAmbiguous(alignment: Alignment, station: Station): boolean {
  const distances = new Set(locateStation(alignment, station).map((p) => p.distance));
  return distances.size > 1;
}

export function distanceAt(alignment: Alignment, station: Station, series?: number): number {
  const positions = locateStation(alignment, station);
  if (positions.length === 0) {
    throw new BadInputError('station is not on this alignment', {
      alignment: alignment.id,
      station: formatStation(station),
    });
  }
  if (series !== undefined) {
    const wanted = positions.find((p) => p.series === series);
    if (wanted === undefined) {
      throw new BadInputError('station is not in that series', {
        alignment: alignment.id,
        station: formatStation(station),
        series,
      });
    }
    return wanted.distance;
  }
  const distances = [...new Set(positions.map((p) => p.distance))];
  const only = distances[0];
  if (distances.length > 1 || only === undefined) {
    throw new ConflictError('station is ambiguous, name the series', {
      alignment: alignment.id,
      station: formatStation(station),
      series: positions.map((p) => p.series).join(','),
    });
  }
  return only;
}

/** Ground distance between two labels. Negative when they are back to front. */
export function trueLength(alignment: Alignment, from: Station, to: Station): number {
  return distanceAt(alignment, to) - distanceAt(alignment, from);
}

/** The label at a given distance along the ground, and the series it is in. */
export function stationAtDistance(
  alignment: Alignment,
  distance: number,
): { station: Station; series: number } {
  if (!Number.isFinite(distance) || distance < 0) {
    throw new BadInputError('distance along the alignment must not be negative', { distance });
  }
  for (const segment of alignmentSegments(alignment)) {
    const length = segment.to - segment.from;
    if (distance <= segment.startDistance + length) {
      const offset = distance - segment.startDistance;
      return { station: asStation(segment.from + offset), series: segment.series };
    }
  }
  throw new BadInputError('distance runs off the end of the alignment', {
    alignment: alignment.id,
    distance,
    length: alignmentLength(alignment),
  });
}
