import { BadInputError } from '../../errors.js';

/**
 * The units a civil bill of quantities is written in. This is the CESMM set
 * plus the two time units that turn up in dayworks, and nothing else: a unit
 * the bill cannot be written in is a unit nothing can be claimed against.
 */
export type UnitCode = 'm' | 'm2' | 'm3' | 'ha' | 'nr' | 't' | 'kg' | 'hr' | 'wk' | 'sum';

export type Dimension = 'length' | 'area' | 'volume' | 'mass' | 'count' | 'time' | 'lump';

export interface UnitDef {
  readonly code: UnitCode;
  readonly name: string;
  readonly dimension: Dimension;
  /** Decimal places a measured quantity is billed to. */
  readonly decimals: 0 | 1 | 2 | 3;
  /**
   * False for a lump sum. Nothing measures a `sum` item: it is earned through a
   * rule of credit or not at all, and treating it as a quantity would let a
   * survey book half of it.
   */
  readonly measurable: boolean;
}

const DEFS: Readonly<Record<UnitCode, UnitDef>> = {
  m: { code: 'm', name: 'linear metre', dimension: 'length', decimals: 2, measurable: true },
  m2: { code: 'm2', name: 'square metre', dimension: 'area', decimals: 2, measurable: true },
  m3: { code: 'm3', name: 'cubic metre', dimension: 'volume', decimals: 2, measurable: true },
  ha: { code: 'ha', name: 'hectare', dimension: 'area', decimals: 3, measurable: true },
  nr: { code: 'nr', name: 'number', dimension: 'count', decimals: 0, measurable: true },
  t: { code: 't', name: 'tonne', dimension: 'mass', decimals: 3, measurable: true },
  kg: { code: 'kg', name: 'kilogram', dimension: 'mass', decimals: 1, measurable: true },
  hr: { code: 'hr', name: 'hour', dimension: 'time', decimals: 2, measurable: true },
  wk: { code: 'wk', name: 'week', dimension: 'time', decimals: 2, measurable: true },
  sum: { code: 'sum', name: 'lump sum', dimension: 'lump', decimals: 0, measurable: false },
};

export const ALL_UNITS: readonly UnitCode[] = Object.keys(DEFS) as UnitCode[];

/** What site paperwork actually writes, keyed to what the bill means. */
const ALIASES: Readonly<Record<string, UnitCode>> = {
  lm: 'm',
  'lin m': 'm',
  'lin.m': 'm',
  metre: 'm',
  meter: 'm',
  m2: 'm2',
  'm²': 'm2',
  sqm: 'm2',
  'sq m': 'm2',
  sm: 'm2',
  m3: 'm3',
  'm³': 'm3',
  cum: 'm3',
  'cu m': 'm3',
  cm: 'm3',
  no: 'nr',
  'no.': 'nr',
  num: 'nr',
  each: 'nr',
  ea: 'nr',
  item: 'nr',
  te: 't',
  tonne: 't',
  tonnes: 't',
  ton: 't',
  hour: 'hr',
  hours: 'hr',
  h: 'hr',
  week: 'wk',
  weeks: 'wk',
  ls: 'sum',
  'lump sum': 'sum',
  hectare: 'ha',
};

export function isUnitCode(value: string): value is UnitCode {
  return Object.prototype.hasOwnProperty.call(DEFS, value);
}

export function unitDef(code: UnitCode): UnitDef {
  const def = DEFS[code];
  return def;
}

/** Case and punctuation insensitive, because nobody types `m3` on a site sheet. */
export function parseUnit(text: string): UnitCode {
  const cleaned = text.trim().toLowerCase().replace(/\s+/g, ' ');
  if (cleaned === '') {
    throw new BadInputError('unit is blank');
  }
  if (isUnitCode(cleaned)) return cleaned;
  const alias = ALIASES[cleaned];
  if (alias !== undefined) return alias;
  throw new BadInputError('unknown unit', { text: text.trim() });
}

export function unitName(code: UnitCode): string {
  return unitDef(code).name;
}

export function unitDecimals(code: UnitCode): number {
  return unitDef(code).decimals;
}

export function isMeasurable(code: UnitCode): boolean {
  return unitDef(code).measurable;
}

export function dimensionOf(code: UnitCode): Dimension {
  return unitDef(code).dimension;
}

/**
 * Two units may be added only when they are the same unit. Same dimension is
 * not enough: a tonne and a kilogram both weigh things, and silently converting
 * one to the other would hide a bill written against the wrong item.
 */
export function assertSameUnit(a: UnitCode, b: UnitCode): void {
  if (a !== b) {
    throw new BadInputError('cannot combine quantities in different units', {
      left: a,
      right: b,
    });
  }
}
