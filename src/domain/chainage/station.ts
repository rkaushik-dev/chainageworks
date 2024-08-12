import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';

declare const stationBrand: unique symbol;

/**
 * A point on the alignment, held as whole millimetres from the origin.
 *
 * Millimetres because setting out is issued to the millimetre and because
 * floating point metres drift: adding 0.1 m two hundred times does not land on
 * 20 m. Every length in this package is an integer count of millimetres and the
 * conversion to metres happens once, at the edge, when something is printed.
 */
export type Station = number & { readonly [stationBrand]: true };

export const MM_PER_METRE = 1000;
export const MM_PER_KM = 1_000_000;

/** `CH 12+340.500`, `12+340.5`, `12+340`, and the negative forms of each. */
const STATION_TEXT = /^(?:CH\.?\s*)?(-)?(\d+)\+(\d+)(?:\.(\d{1,3}))?$/i;

export function asStation(millimetres: number): Station {
  if (!Number.isFinite(millimetres)) {
    throw new BadInputError('chainage must be a finite number', { value: String(millimetres) });
  }
  if (!Number.isInteger(millimetres)) {
    throw new BadInputError('chainage must be a whole number of millimetres', {
      value: millimetres,
    });
  }
  return millimetres as Station;
}

/** Metres in, millimetres out, rounded half away from zero. */
export function stationFromMetres(metres: number): Station {
  if (!Number.isFinite(metres)) {
    throw new BadInputError('chainage must be a finite number', { value: String(metres) });
  }
  const scaled = metres * MM_PER_METRE;
  const rounded = scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
  return asStation(rounded);
}

export function stationToMetres(station: Station): number {
  return station / MM_PER_METRE;
}

/**
 * Read a chainage off a drawing or a site record. The metres part is the offset
 * inside the kilometre, so it has to stay under 1000: `12+1340` is a slip for
 * `13+340` often enough that accepting it would hide the mistake.
 */
export function tryParseStation(text: string): Result<Station> {
  const trimmed = text.trim();
  if (trimmed === '') {
    return err(new BadInputError('chainage is blank'));
  }
  const m = STATION_TEXT.exec(trimmed);
  if (m === null) {
    return err(new BadInputError('chainage does not look like km+metres', { text: trimmed }));
  }
  const [, sign, kmText, metreText, fractionText] = m;
  const metres = Number(metreText);
  if (metres >= 1000) {
    return err(
      new BadInputError('the metres part of a chainage must be under 1000', {
        text: trimmed,
        metres,
      }),
    );
  }
  const fraction = (fractionText ?? '').padEnd(3, '0');
  const magnitude = Number(kmText) * MM_PER_KM + metres * MM_PER_METRE + Number(fraction);
  return ok(asStation(sign === '-' ? -magnitude : magnitude));
}

export function parseStation(text: string): Station {
  const r = tryParseStation(text);
  if (!r.ok) throw r.error;
  return r.value;
}

export interface FormatStationOptions {
  /** Millimetres shown after the decimal point. Three by default. */
  readonly decimals?: 0 | 1 | 2 | 3;
  /** Prepend `CH `, the way a drawing label reads. */
  readonly prefix?: boolean;
}

export function formatStation(station: Station, options: FormatStationOptions = {}): string {
  const decimals = options.decimals ?? 3;
  const negative = station < 0;
  const magnitude = Math.abs(station);
  const km = Math.floor(magnitude / MM_PER_KM);
  const withinKm = magnitude - km * MM_PER_KM;
  const metres = Math.floor(withinKm / MM_PER_METRE);
  const millimetres = withinKm - metres * MM_PER_METRE;
  const metrePart = String(metres).padStart(3, '0');
  const tail =
    decimals === 0 ? '' : `.${String(millimetres).padStart(3, '0').slice(0, decimals)}`;
  return `${options.prefix === true ? 'CH ' : ''}${negative ? '-' : ''}${km}+${metrePart}${tail}`;
}

export function compareStations(a: Station, b: Station): number {
  return a - b;
}

export function stationPlus(station: Station, millimetres: number): Station {
  return asStation(station + millimetres);
}

export function stationMinus(station: Station, millimetres: number): Station {
  return asStation(station - millimetres);
}

/** Unsigned distance between two points, in millimetres. */
export function stationSpan(a: Station, b: Station): number {
  return Math.abs(a - b);
}

export function minStation(a: Station, b: Station): Station {
  return a <= b ? a : b;
}

export function maxStation(a: Station, b: Station): Station {
  return a >= b ? a : b;
}

export function clampStation(station: Station, low: Station, high: Station): Station {
  if (low > high) {
    throw new BadInputError('clamp bounds are the wrong way round', { low, high });
  }
  return minStation(maxStation(station, low), high);
}
