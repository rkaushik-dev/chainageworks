import { describe, expect, it } from 'vitest';
import { formatStation, parseStation } from '../../../../src/domain/chainage/station.js';
import { sectionFromMetres } from '../../../../src/domain/earthworks/crossSection.js';
import {
  balancePoints,
  buildHaulRun,
  closingOrdinate,
  DEFAULT_FREE_HAUL_METRES,
  describeHaul,
  haulMoment,
  isSurplus,
  overhaul,
} from '../../../../src/domain/earthworks/massHaul.js';

const at = parseStation;
const section = (station: string, cut: number, fill: number) =>
  sectionFromMetres(at(station), cut, fill);

/** A hundred metres of cut and a hundred of matching fill, so it closes out. */
const balanced = [
  section('12+000', 40, 0),
  section('12+100', 40, 0),
  section('12+200', 0, 40),
  section('12+300', 0, 40),
];

/** The same run with a deeper embankment, so it finishes short of fill. */
const deficit = [
  section('12+000', 40, 0),
  section('12+100', 40, 0),
  section('12+200', 0, 60),
  section('12+300', 0, 60),
];

describe('buildHaulRun', () => {
  it('starts at nothing and adds each span', () => {
    const run = buildHaulRun(balanced);
    expect(run.ordinates).toHaveLength(4);
    expect(run.ordinates[0]?.ordinate).toBe(0);
    expect(run.ordinates.map((o) => o.ordinate / 1000)).toEqual([0, 4000, 4000, 0]);
  });

  it('is empty for no sections', () => {
    const run = buildHaulRun([]);
    expect(run.ordinates).toEqual([]);
    expect(closingOrdinate(run).milli).toBe(0);
  });

  it('applies the bulking factor to the cut', () => {
    const run = buildHaulRun(balanced, 1.2);
    expect(run.ordinates[1]?.ordinate).toBe(4_800_000);
  });

  it('refuses a bulking factor that is not positive', () => {
    expect(() => buildHaulRun(balanced, 0)).toThrow(/positive number/);
    expect(() => buildHaulRun(balanced, Number.NaN)).toThrow(/positive number/);
  });
});

describe('the closing ordinate', () => {
  it('is a deficit when the fill outruns the cut', () => {
    const run = buildHaulRun(deficit);
    expect(closingOrdinate(run).milli).toBe(-3_000_000);
    expect(isSurplus(run)).toBe(false);
  });

  it('is a surplus when the cut outruns the fill', () => {
    const run = buildHaulRun([section('12+000', 40, 0), section('12+100', 40, 0)]);
    expect(isSurplus(run)).toBe(true);
    expect(closingOrdinate(run).milli).toBe(4_000_000);
  });

  it('is nothing when the two balance exactly', () => {
    expect(closingOrdinate(buildHaulRun(balanced)).milli).toBe(0);
  });

  it('turns a deficit into a surplus with enough bulking', () => {
    expect(isSurplus(buildHaulRun(deficit, 4))).toBe(true);
  });
});

describe('balancePoints', () => {
  it('finds the chainage the curve crosses back through', () => {
    const run = buildHaulRun(deficit);
    const points = balancePoints(run).map((s) => formatStation(s, { decimals: 0 }));
    expect(points).toEqual(['12+250']);
  });

  it('finds nothing on a curve that never crosses', () => {
    const run = buildHaulRun([section('12+000', 40, 0), section('12+100', 40, 0)]);
    expect(balancePoints(run)).toEqual([]);
  });

  it('finds a stretch that closes out exactly', () => {
    const points = balancePoints(buildHaulRun(balanced)).map((s) =>
      formatStation(s, { decimals: 0 }),
    );
    expect(points).toEqual(['12+300']);
  });

  it('does not report the start, which is only where the count began', () => {
    const run = buildHaulRun([
      section('12+000', 0, 0),
      section('12+100', 40, 0),
      section('12+200', 40, 0),
    ]);
    expect(balancePoints(run)).toEqual([]);
  });
});

describe('haulMoment', () => {
  it('is nothing on a run with no material to move', () => {
    const flat = buildHaulRun([section('12+000', 0, 0), section('12+100', 0, 0)]);
    expect(haulMoment(flat)).toBe(0);
  });

  it('grows with the distance the material has to travel', () => {
    const near = buildHaulRun([
      section('12+000', 40, 0),
      section('12+100', 40, 0),
      section('12+200', 0, 40),
    ]);
    const far = buildHaulRun([
      section('12+000', 40, 0),
      section('12+100', 40, 0),
      section('12+600', 0, 40),
    ]);
    expect(haulMoment(far)).toBeGreaterThan(haulMoment(near));
  });

  it('grows with the volume being moved', () => {
    const light = buildHaulRun([section('12+000', 10, 0), section('12+200', 10, 0)]);
    const heavy = buildHaulRun([section('12+000', 40, 0), section('12+200', 40, 0)]);
    expect(haulMoment(heavy)).toBeGreaterThan(haulMoment(light));
  });
});

describe('overhaul', () => {
  const run = buildHaulRun([
    section('12+000', 40, 0),
    section('12+100', 40, 0),
    section('13+000', 0, 40),
  ]);

  it('gives everything away inside the free haul', () => {
    const split = overhaul(run, 10_000);
    expect(split.overhaulMoment).toBe(0);
    expect(split.freeMoment).toBe(haulMoment(run));
  });

  it('pays for everything when nothing is free', () => {
    const split = overhaul(run, 0);
    expect(split.freeMoment).toBe(0);
    expect(split.overhaulMoment).toBe(haulMoment(run));
  });

  it('splits the moment at the free haul distance', () => {
    const split = overhaul(run);
    expect(split.freeHaulMetres).toBe(DEFAULT_FREE_HAUL_METRES);
    expect(split.freeMoment).toBeGreaterThan(0);
    expect(split.overhaulMoment).toBeGreaterThan(0);
    expect(split.freeMoment + split.overhaulMoment).toBeCloseTo(haulMoment(run), 1);
  });

  it('refuses a negative free haul distance', () => {
    expect(() => overhaul(run, -1)).toThrow(/not negative/);
  });
});

describe('describeHaul', () => {
  it('says which way the run is out and where it balances', () => {
    expect(describeHaul(buildHaulRun(deficit))).toBe(
      'deficit of 3000 m3, 1 balance points at 12+250',
    );
  });

  it('says so when there is nothing to move', () => {
    const flat = buildHaulRun([section('12+000', 0, 0), section('12+100', 0, 0)]);
    expect(describeHaul(flat)).toContain('balanced');
  });
});
