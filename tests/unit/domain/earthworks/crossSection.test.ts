import { describe, expect, it } from 'vitest';
import { chainageRange } from '../../../../src/domain/chainage/range.js';
import { formatStation, parseStation } from '../../../../src/domain/chainage/station.js';
import { formatQuantity } from '../../../../src/domain/quantity/quantity.js';
import {
  balanceAfterBulking,
  crossSection,
  endAreaVolume,
  interpolate,
  isBorrowNeeded,
  prismoidalVolume,
  sectionFromMetres,
  sortSections,
  totalVolumes,
  volumesOver,
} from '../../../../src/domain/earthworks/crossSection.js';

const at = parseStation;
const section = (station: string, cut: number, fill: number) =>
  sectionFromMetres(at(station), cut, fill);

describe('crossSection', () => {
  it('holds areas as whole thousandths', () => {
    expect(section('12+000', 40.5, 0).cutArea).toBe(40_500);
    expect(crossSection(at('12+000'), 40_500, 0).cutArea).toBe(40_500);
  });

  it('rounds a survey area to the thousandth', () => {
    expect(section('12+000', 40.5004, 0).cutArea).toBe(40_500);
  });

  it('refuses a negative or fractional area', () => {
    expect(() => crossSection(at('12+000'), -1, 0)).toThrow(/not negative/);
    expect(() => crossSection(at('12+000'), 1.5, 0)).toThrow(/whole number/);
  });
});

describe('sortSections', () => {
  it('puts them in chainage order', () => {
    const sorted = sortSections([section('12+040', 1, 0), section('12+000', 2, 0)]);
    expect(sorted.map((s) => formatStation(s.station))).toEqual(['12+000.000', '12+040.000']);
  });

  it('refuses two sections at the same chainage', () => {
    expect(() => sortSections([section('12+000', 1, 0), section('12+000', 2, 0)])).toThrow(
      /same chainage/,
    );
  });
});

describe('endAreaVolume', () => {
  it('averages the two end areas over the length', () => {
    const volume = endAreaVolume(section('12+000', 40, 0), section('12+020', 60, 0));
    expect(formatQuantity(volume.cut)).toBe('1000.00 m3');
    expect(volume.fill.milli).toBe(0);
  });

  it('measures cut and fill separately', () => {
    const volume = endAreaVolume(section('12+000', 40, 10), section('12+020', 0, 30));
    expect(formatQuantity(volume.cut)).toBe('400.00 m3');
    expect(formatQuantity(volume.fill)).toBe('400.00 m3');
  });

  it('is nothing between two sections with no area', () => {
    const volume = endAreaVolume(section('12+000', 0, 0), section('12+020', 0, 0));
    expect(volume.cut.milli).toBe(0);
  });

  it('is nothing over no distance at all', () => {
    const volume = endAreaVolume(section('12+000', 40, 0), section('12+000', 60, 0));
    expect(volume.cut.milli).toBe(0);
  });

  it('does not care which way round the sections come', () => {
    const forward = endAreaVolume(section('12+000', 40, 0), section('12+020', 60, 0));
    const back = endAreaVolume(section('12+020', 60, 0), section('12+000', 40, 0));
    expect(back.cut).toEqual(forward.cut);
  });
});

describe('prismoidalVolume', () => {
  it('matches end area when the section changes evenly', () => {
    const a = section('12+000', 40, 0);
    const middle = section('12+010', 50, 0);
    const b = section('12+020', 60, 0);
    expect(prismoidalVolume(a, middle, b).cut).toEqual(endAreaVolume(a, b).cut);
  });

  it('reads less than end area when the middle pinches', () => {
    const a = section('12+000', 40, 0);
    const b = section('12+020', 60, 0);
    const pinched = section('12+010', 30, 0);
    expect(prismoidalVolume(a, pinched, b).cut.milli).toBeLessThan(
      endAreaVolume(a, b).cut.milli,
    );
  });

  it('reads more than end area when the middle swells', () => {
    const a = section('12+000', 40, 0);
    const b = section('12+020', 60, 0);
    const swollen = section('12+010', 70, 0);
    expect(prismoidalVolume(a, swollen, b).cut.milli).toBeGreaterThan(
      endAreaVolume(a, b).cut.milli,
    );
  });
});

describe('totalVolumes', () => {
  const sections = [
    section('12+000', 0, 0),
    section('12+020', 40, 0),
    section('12+040', 60, 0),
    section('12+060', 0, 20),
  ];

  it('adds every span between consecutive sections', () => {
    const volume = totalVolumes(sections);
    expect(formatQuantity(volume.cut)).toBe('2000.00 m3');
    expect(formatQuantity(volume.fill)).toBe('200.00 m3');
  });

  it('is nothing for a single section or none at all', () => {
    expect(totalVolumes([section('12+000', 40, 0)]).cut.milli).toBe(0);
    expect(totalVolumes([]).cut.milli).toBe(0);
  });

  it('does not care what order they arrive in', () => {
    expect(totalVolumes([...sections].reverse())).toEqual(totalVolumes(sections));
  });
});

describe('interpolate', () => {
  it('takes the areas straight between two sections', () => {
    const middle = interpolate(
      section('12+000', 40, 0),
      section('12+020', 60, 0),
      at('12+010'),
    );
    expect(middle.cutArea).toBe(50_000);
    expect(formatStation(middle.station)).toBe('12+010.000');
  });

  it('lands on the end areas at the ends', () => {
    const a = section('12+000', 40, 5);
    const b = section('12+020', 60, 15);
    expect(interpolate(a, b, at('12+000')).cutArea).toBe(a.cutArea);
    expect(interpolate(a, b, at('12+020')).fillArea).toBe(b.fillArea);
  });
});

describe('volumesOver', () => {
  const sections = [
    section('12+000', 0, 0),
    section('12+020', 40, 0),
    section('12+040', 40, 0),
    section('12+060', 0, 0),
  ];

  it('measures a stretch that lands on the surveyed sections', () => {
    const volume = volumesOver(sections, chainageRange(at('12+020'), at('12+040')));
    expect(formatQuantity(volume.cut)).toBe('800.00 m3');
  });

  it('interpolates a section at a boundary that falls between two', () => {
    const volume = volumesOver(sections, chainageRange(at('12+010'), at('12+050')));
    expect(formatQuantity(volume.cut)).toBe('1400.00 m3');
  });

  it('measures the lot when the extent covers everything', () => {
    expect(volumesOver(sections, chainageRange(at('11+000'), at('13+000')))).toEqual(
      totalVolumes(sections),
    );
  });

  it('measures nothing over a stretch outside the survey', () => {
    expect(volumesOver(sections, chainageRange(at('13+000'), at('13+100'))).cut.milli).toBe(0);
  });

  it('adds back to the whole when a stretch is cut in two', () => {
    const whole = volumesOver(sections, chainageRange(at('12+000'), at('12+060')));
    const first = volumesOver(sections, chainageRange(at('12+000'), at('12+030')));
    const second = volumesOver(sections, chainageRange(at('12+030'), at('12+060')));
    expect(first.cut.milli + second.cut.milli).toBe(whole.cut.milli);
  });
});

describe('balanceAfterBulking', () => {
  const volumes = totalVolumes([section('12+000', 40, 10), section('12+020', 40, 10)]);

  it('is the cut less the fill when nothing bulks', () => {
    expect(formatQuantity(volumes.cut)).toBe('800.00 m3');
    expect(formatQuantity(volumes.fill)).toBe('200.00 m3');
    expect(formatQuantity(balanceAfterBulking(volumes, 1))).toBe('600.00 m3');
  });

  it('goes further when the material bulks', () => {
    expect(balanceAfterBulking(volumes, 1.2).milli).toBeGreaterThan(
      balanceAfterBulking(volumes, 1).milli,
    );
  });

  it('runs short when the material shrinks on compaction', () => {
    expect(balanceAfterBulking(volumes, 0.2).milli).toBeLessThan(0);
    expect(isBorrowNeeded(volumes, 0.2)).toBe(true);
    expect(isBorrowNeeded(volumes, 1)).toBe(false);
  });

  it('refuses a factor that is not positive', () => {
    expect(() => balanceAfterBulking(volumes, 0)).toThrow(/positive number/);
    expect(() => balanceAfterBulking(volumes, Number.NaN)).toThrow(/positive number/);
  });
});
