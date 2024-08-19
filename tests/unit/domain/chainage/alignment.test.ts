import { describe, expect, it } from 'vitest';
import { BadInputError, ConflictError } from '../../../../src/errors.js';
import {
  alignmentLength,
  alignmentSegments,
  buildAlignment,
  describeEquation,
  distanceAt,
  equationKind,
  isStationAmbiguous,
  isStationOnAlignment,
  locateStation,
  stationAtDistance,
  trueLength,
} from '../../../../src/domain/chainage/alignment.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';

const at = parseStation;

const plain = buildAlignment({
  id: 'A1',
  name: 'main carriageway',
  start: at('0+000'),
  end: at('4+000'),
});

/** Thirty metres of chainage exist twice: 12+310 through 12+340. */
const overlapping = buildAlignment({
  id: 'A2',
  name: 'diverted section',
  start: at('12+000'),
  end: at('13+000'),
  equations: [{ back: at('12+340'), ahead: at('12+310') }],
});

/** Twenty metres of chainage do not exist at all: 12+340 through 12+360. */
const gapped = buildAlignment({
  id: 'A3',
  name: 'shortened section',
  start: at('12+000'),
  end: at('13+000'),
  equations: [{ back: at('12+340'), ahead: at('12+360') }],
});

describe('equationKind', () => {
  it('names the three shapes', () => {
    expect(equationKind({ back: at('12+340'), ahead: at('12+310') })).toBe('overlap');
    expect(equationKind({ back: at('12+340'), ahead: at('12+360') })).toBe('gap');
    expect(equationKind({ back: at('12+340'), ahead: at('12+340') })).toBe('continuous');
  });
});

describe('describeEquation', () => {
  it('writes the surveyor form', () => {
    expect(describeEquation({ back: at('12+340'), ahead: at('12+310') })).toBe(
      '12+340.000 back = 12+310.000 ahead',
    );
  });
});

describe('alignmentSegments', () => {
  it('gives one segment when there are no equations', () => {
    expect(alignmentSegments(plain)).toEqual([
      { series: 0, from: 0, to: 4_000_000, startDistance: 0 },
    ]);
  });

  it('splits at each equation and carries the running distance', () => {
    expect(alignmentSegments(overlapping)).toEqual([
      { series: 0, from: 12_000_000, to: 12_340_000, startDistance: 0 },
      { series: 1, from: 12_310_000, to: 13_000_000, startDistance: 340_000 },
    ]);
  });

  it('refuses an equation that does not advance', () => {
    expect(() =>
      buildAlignment({
        id: 'bad',
        name: 'folded',
        start: at('12+000'),
        end: at('13+000'),
        equations: [{ back: at('11+900'), ahead: at('12+500') }],
      }),
    ).toThrow(/must sit ahead of the series it closes/);
  });

  it('refuses an equation that lands exactly on the series start', () => {
    expect(() =>
      buildAlignment({
        id: 'bad',
        name: 'zero length',
        start: at('12+000'),
        end: at('13+000'),
        equations: [{ back: at('12+000'), ahead: at('12+500') }],
      }),
    ).toThrow(BadInputError);
  });

  it('refuses an alignment that ends before its last series starts', () => {
    expect(() =>
      buildAlignment({
        id: 'bad',
        name: 'backwards end',
        start: at('12+000'),
        end: at('12+200'),
        equations: [{ back: at('12+100'), ahead: at('12+500') }],
      }),
    ).toThrow(/ends at or before/);
  });

  it('handles two equations in a row', () => {
    const twice = buildAlignment({
      id: 'A4',
      name: 'twice diverted',
      start: at('0+000'),
      end: at('3+000'),
      equations: [
        { back: at('1+000'), ahead: at('0+950') },
        { back: at('2+000'), ahead: at('2+100') },
      ],
    });
    expect(alignmentSegments(twice)).toEqual([
      { series: 0, from: 0, to: 1_000_000, startDistance: 0 },
      { series: 1, from: 950_000, to: 2_000_000, startDistance: 1_000_000 },
      { series: 2, from: 2_100_000, to: 3_000_000, startDistance: 2_050_000 },
    ]);
  });
});

describe('alignmentLength', () => {
  it('is the plain difference with no equations', () => {
    expect(alignmentLength(plain)).toBe(4_000_000);
  });

  it('counts the overlapped thirty metres twice, because it is built twice', () => {
    expect(alignmentLength(overlapping)).toBe(1_030_000);
  });

  it('leaves out the twenty metres a gap skips', () => {
    expect(alignmentLength(gapped)).toBe(980_000);
  });
});

describe('locateStation', () => {
  it('finds one place on a continuous alignment', () => {
    expect(locateStation(plain, at('1+500'))).toEqual([{ series: 0, distance: 1_500_000 }]);
  });

  it('finds both places inside an overlap', () => {
    expect(locateStation(overlapping, at('12+320'))).toEqual([
      { series: 0, distance: 320_000 },
      { series: 1, distance: 350_000 },
    ]);
  });

  it('finds nothing inside a gap', () => {
    expect(locateStation(gapped, at('12+350'))).toEqual([]);
  });

  it('finds nothing off either end', () => {
    expect(locateStation(plain, at('5+000'))).toEqual([]);
    expect(locateStation(overlapping, at('11+000'))).toEqual([]);
  });

  it('reports the shared end of a continuous equation once, by distance', () => {
    const continuous = buildAlignment({
      id: 'A5',
      name: 'paper equation',
      start: at('0+000'),
      end: at('2+000'),
      equations: [{ back: at('1+000'), ahead: at('1+000') }],
    });
    expect(isStationAmbiguous(continuous, at('1+000'))).toBe(false);
    expect(distanceAt(continuous, at('1+000'))).toBe(1_000_000);
  });
});

describe('isStationOnAlignment', () => {
  it('answers for both ends and for a gap', () => {
    expect(isStationOnAlignment(plain, at('0+000'))).toBe(true);
    expect(isStationOnAlignment(plain, at('4+000'))).toBe(true);
    expect(isStationOnAlignment(gapped, at('12+350'))).toBe(false);
  });
});

describe('isStationAmbiguous', () => {
  it('is true only inside an overlap', () => {
    expect(isStationAmbiguous(overlapping, at('12+320'))).toBe(true);
    expect(isStationAmbiguous(overlapping, at('12+100'))).toBe(false);
    expect(isStationAmbiguous(overlapping, at('12+500'))).toBe(false);
    expect(isStationAmbiguous(plain, at('1+000'))).toBe(false);
  });
});

describe('distanceAt', () => {
  it('measures from the start of the alignment', () => {
    expect(distanceAt(plain, at('1+500'))).toBe(1_500_000);
  });

  it('refuses a station that is not on the alignment', () => {
    expect(() => distanceAt(gapped, at('12+350'))).toThrow(/not on this alignment/);
  });

  it('refuses an ambiguous station until a series is named', () => {
    expect(() => distanceAt(overlapping, at('12+320'))).toThrow(ConflictError);
    expect(distanceAt(overlapping, at('12+320'), 0)).toBe(320_000);
    expect(distanceAt(overlapping, at('12+320'), 1)).toBe(350_000);
  });

  it('refuses a series the station is not in', () => {
    expect(() => distanceAt(overlapping, at('12+100'), 1)).toThrow(/not in that series/);
  });
});

describe('trueLength', () => {
  it('is the plain difference when nothing jumps', () => {
    expect(trueLength(plain, at('1+000'), at('1+500'))).toBe(500_000);
  });

  it('drops the skipped ground across a gap', () => {
    expect(trueLength(gapped, at('12+300'), at('12+400'))).toBe(80_000);
  });

  it('is negative when the stations are given the wrong way round', () => {
    expect(trueLength(plain, at('1+500'), at('1+000'))).toBe(-500_000);
  });
});

describe('stationAtDistance', () => {
  it('walks forward on a plain alignment', () => {
    expect(stationAtDistance(plain, 1_500_000)).toEqual({ station: 1_500_000, series: 0 });
  });

  it('returns the earlier series at a boundary distance', () => {
    expect(stationAtDistance(overlapping, 340_000)).toEqual({
      station: 12_340_000,
      series: 0,
    });
  });

  it('crosses into the next series', () => {
    expect(stationAtDistance(overlapping, 350_000)).toEqual({
      station: 12_320_000,
      series: 1,
    });
  });

  it('reaches the far end exactly', () => {
    expect(stationAtDistance(plain, 4_000_000)).toEqual({ station: 4_000_000, series: 0 });
  });

  it('refuses a negative distance', () => {
    expect(() => stationAtDistance(plain, -1)).toThrow(/must not be negative/);
  });

  it('refuses a distance past the end', () => {
    expect(() => stationAtDistance(plain, 4_000_001)).toThrow(/runs off the end/);
  });
});
