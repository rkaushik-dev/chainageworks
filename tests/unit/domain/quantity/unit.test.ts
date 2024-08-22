import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  ALL_UNITS,
  assertSameUnit,
  dimensionOf,
  isMeasurable,
  isUnitCode,
  parseUnit,
  unitDecimals,
  unitDef,
  unitName,
} from '../../../../src/domain/quantity/unit.js';

describe('the unit table', () => {
  it('covers the ten units the bill can be written in', () => {
    expect(ALL_UNITS).toEqual(['m', 'm2', 'm3', 'ha', 'nr', 't', 'kg', 'hr', 'wk', 'sum']);
  });

  it('gives every unit a definition keyed to itself', () => {
    for (const code of ALL_UNITS) {
      expect(unitDef(code).code).toBe(code);
      expect(unitDef(code).name.length).toBeGreaterThan(0);
    }
  });

  it('bills a number to whole units and a tonne to three places', () => {
    expect(unitDecimals('nr')).toBe(0);
    expect(unitDecimals('t')).toBe(3);
    expect(unitDecimals('m3')).toBe(2);
  });

  it('reports the dimension', () => {
    expect(dimensionOf('m')).toBe('length');
    expect(dimensionOf('m2')).toBe('area');
    expect(dimensionOf('ha')).toBe('area');
    expect(dimensionOf('m3')).toBe('volume');
    expect(dimensionOf('kg')).toBe('mass');
    expect(dimensionOf('nr')).toBe('count');
    expect(dimensionOf('wk')).toBe('time');
    expect(dimensionOf('sum')).toBe('lump');
  });

  it('marks the lump sum as the one unit nothing measures', () => {
    expect(isMeasurable('sum')).toBe(false);
    for (const code of ALL_UNITS.filter((c) => c !== 'sum')) {
      expect(isMeasurable(code)).toBe(true);
    }
  });

  it('names units in full', () => {
    expect(unitName('m3')).toBe('cubic metre');
    expect(unitName('sum')).toBe('lump sum');
  });
});

describe('isUnitCode', () => {
  it('accepts the canonical codes only', () => {
    expect(isUnitCode('m3')).toBe(true);
    expect(isUnitCode('M3')).toBe(false);
    expect(isUnitCode('cum')).toBe(false);
    expect(isUnitCode('toString')).toBe(false);
  });
});

describe('parseUnit', () => {
  it('takes the canonical code straight through', () => {
    expect(parseUnit('m3')).toBe('m3');
  });

  it('ignores case and outer whitespace', () => {
    expect(parseUnit('  M3 ')).toBe('m3');
    expect(parseUnit('NR')).toBe('nr');
  });

  it('reads what a site sheet writes for volume', () => {
    for (const text of ['m³', 'CUM', 'cu m', 'cu  m']) {
      expect(parseUnit(text)).toBe('m3');
    }
  });

  it('reads what a site sheet writes for area', () => {
    for (const text of ['m²', 'SQM', 'sq m', 'sm']) {
      expect(parseUnit(text)).toBe('m2');
    }
  });

  it('reads the several spellings of a count', () => {
    for (const text of ['No.', 'no', 'NUM', 'each', 'EA', 'Item']) {
      expect(parseUnit(text)).toBe('nr');
    }
  });

  it('reads linear metres however they are abbreviated', () => {
    for (const text of ['LM', 'lin m', 'lin.m', 'METRE', 'meter']) {
      expect(parseUnit(text)).toBe('m');
    }
  });

  it('reads mass and time', () => {
    expect(parseUnit('te')).toBe('t');
    expect(parseUnit('Tonnes')).toBe('t');
    expect(parseUnit('hours')).toBe('hr');
    expect(parseUnit('H')).toBe('hr');
    expect(parseUnit('weeks')).toBe('wk');
  });

  it('reads a lump sum', () => {
    expect(parseUnit('LS')).toBe('sum');
    expect(parseUnit('lump sum')).toBe('sum');
  });

  it('refuses a blank', () => {
    expect(() => parseUnit('  ')).toThrow(/blank/);
  });

  it('refuses anything it does not know, and quotes it back', () => {
    expect(() => parseUnit('furlong')).toThrow(BadInputError);
    try {
      parseUnit(' furlong ');
    } catch (e) {
      expect((e as BadInputError).details).toEqual({ text: 'furlong' });
    }
  });
});

describe('assertSameUnit', () => {
  it('passes for the same unit', () => {
    expect(() => {
      assertSameUnit('m3', 'm3');
    }).not.toThrow();
  });

  it('refuses two units of the same dimension', () => {
    expect(() => {
      assertSameUnit('t', 'kg');
    }).toThrow(/different units/);
  });

  it('refuses two units of different dimensions', () => {
    expect(() => {
      assertSameUnit('m', 'm3');
    }).toThrow(/different units/);
  });
});
