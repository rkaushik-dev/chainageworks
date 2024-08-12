import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  asStation,
  clampStation,
  compareStations,
  formatStation,
  maxStation,
  minStation,
  parseStation,
  stationFromMetres,
  stationMinus,
  stationPlus,
  stationSpan,
  stationToMetres,
  tryParseStation,
} from '../../../../src/domain/chainage/station.js';

describe('asStation', () => {
  it('accepts whole millimetres', () => {
    expect(asStation(12_340_500)).toBe(12_340_500);
    expect(asStation(0)).toBe(0);
    expect(asStation(-50_000)).toBe(-50_000);
  });

  it('refuses a fractional millimetre', () => {
    expect(() => asStation(12.5)).toThrow(BadInputError);
    expect(() => asStation(12.5)).toThrow(/whole number of millimetres/);
  });

  it('refuses anything that is not finite', () => {
    expect(() => asStation(Number.NaN)).toThrow(/finite/);
    expect(() => asStation(Number.POSITIVE_INFINITY)).toThrow(/finite/);
  });
});

describe('stationFromMetres', () => {
  it('scales metres to millimetres', () => {
    expect(stationFromMetres(12_340.5)).toBe(12_340_500);
  });

  it('rounds half away from zero on both sides', () => {
    expect(stationFromMetres(0.0005)).toBe(1);
    expect(stationFromMetres(-0.0005)).toBe(-1);
    expect(stationFromMetres(0.00049)).toBe(0);
  });

  it('refuses a non finite input', () => {
    expect(() => stationFromMetres(Number.NaN)).toThrow(/finite/);
  });
});

describe('stationToMetres', () => {
  it('is the inverse for values that fit', () => {
    expect(stationToMetres(asStation(12_340_500))).toBe(12_340.5);
    expect(stationToMetres(asStation(0))).toBe(0);
  });
});

describe('parseStation', () => {
  it('reads the full form', () => {
    expect(parseStation('12+340.500')).toBe(12_340_500);
  });

  it('reads a short fraction and pads it', () => {
    expect(parseStation('12+340.5')).toBe(12_340_500);
    expect(parseStation('12+340.05')).toBe(12_340_050);
  });

  it('reads a whole metre with no fraction', () => {
    expect(parseStation('12+340')).toBe(12_340_000);
  });

  it('reads the origin', () => {
    expect(parseStation('0+000')).toBe(0);
  });

  it('accepts the drawing prefix in either case and with a dot', () => {
    expect(parseStation('CH 12+340')).toBe(12_340_000);
    expect(parseStation('ch. 12+340')).toBe(12_340_000);
    expect(parseStation('CH12+340')).toBe(12_340_000);
  });

  it('accepts chainage before the origin', () => {
    expect(parseStation('-0+050')).toBe(-50_000);
    expect(parseStation('-1+000')).toBe(-1_000_000);
  });

  it('ignores surrounding whitespace', () => {
    expect(parseStation('  12+340  ')).toBe(12_340_000);
  });

  it('throws on a blank string', () => {
    expect(() => parseStation('   ')).toThrow(/blank/);
  });

  it('throws when the shape is wrong', () => {
    expect(() => parseStation('12340')).toThrow(/km\+metres/);
    expect(() => parseStation('12+')).toThrow(/km\+metres/);
    expect(() => parseStation('+340')).toThrow(/km\+metres/);
    expect(() => parseStation('twelve+340')).toThrow(/km\+metres/);
  });

  it('rejects a metres part of 1000 or more, which is nearly always a typo', () => {
    const r = tryParseStation('12+1340');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/under 1000/);
  });

  it('rejects more than three decimal places', () => {
    expect(() => parseStation('12+340.5005')).toThrow(/km\+metres/);
  });
});

describe('formatStation', () => {
  it('writes the canonical three decimal form', () => {
    expect(formatStation(asStation(12_340_500))).toBe('12+340.500');
  });

  it('pads the metres part to three digits', () => {
    expect(formatStation(asStation(12_005_000))).toBe('12+005.000');
    expect(formatStation(asStation(500))).toBe('0+000.500');
  });

  it('honours a shorter decimal request', () => {
    expect(formatStation(asStation(12_340_500), { decimals: 0 })).toBe('12+340');
    expect(formatStation(asStation(12_340_500), { decimals: 1 })).toBe('12+340.5');
    expect(formatStation(asStation(12_340_050), { decimals: 2 })).toBe('12+340.05');
  });

  it('adds the drawing prefix on request', () => {
    expect(formatStation(asStation(0), { prefix: true })).toBe('CH 0+000.000');
  });

  it('keeps the sign on negative chainage', () => {
    expect(formatStation(asStation(-50_000))).toBe('-0+050.000');
  });

  it('round trips through the parser', () => {
    for (const mm of [0, 500, 12_340_500, 999_999, 1_000_000, -50_000]) {
      const s = asStation(mm);
      expect(parseStation(formatStation(s))).toBe(mm);
    }
  });
});

describe('station arithmetic', () => {
  it('compares by position', () => {
    expect(compareStations(asStation(10), asStation(20))).toBeLessThan(0);
    expect(compareStations(asStation(20), asStation(10))).toBeGreaterThan(0);
    expect(compareStations(asStation(10), asStation(10))).toBe(0);
  });

  it('adds and subtracts millimetres', () => {
    expect(stationPlus(asStation(1000), 500)).toBe(1500);
    expect(stationMinus(asStation(1000), 500)).toBe(500);
    expect(stationMinus(asStation(0), 500)).toBe(-500);
  });

  it('refuses a fractional step', () => {
    expect(() => stationPlus(asStation(1000), 0.5)).toThrow(/whole number/);
  });

  it('measures an unsigned span in either direction', () => {
    expect(stationSpan(asStation(1000), asStation(1500))).toBe(500);
    expect(stationSpan(asStation(1500), asStation(1000))).toBe(500);
  });

  it('picks the lower and the higher of a pair', () => {
    expect(minStation(asStation(10), asStation(20))).toBe(10);
    expect(maxStation(asStation(10), asStation(20))).toBe(20);
    expect(minStation(asStation(10), asStation(10))).toBe(10);
  });
});

describe('clampStation', () => {
  it('pulls a point inside the bounds', () => {
    const low = asStation(100);
    const high = asStation(200);
    expect(clampStation(asStation(50), low, high)).toBe(100);
    expect(clampStation(asStation(150), low, high)).toBe(150);
    expect(clampStation(asStation(250), low, high)).toBe(200);
  });

  it('refuses bounds that are the wrong way round', () => {
    expect(() => clampStation(asStation(150), asStation(200), asStation(100))).toThrow(
      /wrong way round/,
    );
  });
});
