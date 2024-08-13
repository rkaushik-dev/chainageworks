import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  chainageRange,
  compareRanges,
  coveredLength,
  formatChainageRange,
  intersectRanges,
  isPointRange,
  parseChainageRange,
  rangeContains,
  rangeCovers,
  rangeLength,
  rangeTouches,
  rangesEqual,
  rangesOverlap,
  splitRangeAt,
  subtractRange,
  tryParseChainageRange,
  unionRanges,
} from '../../../../src/domain/chainage/range.js';
import { parseStation } from '../../../../src/domain/chainage/station.js';

const at = parseStation;
const r = (from: string, to: string) => chainageRange(at(from), at(to));

describe('chainageRange', () => {
  it('holds the two stations it was given', () => {
    const range = r('12+000', '12+340');
    expect(range.from).toBe(12_000_000);
    expect(range.to).toBe(12_340_000);
  });

  it('allows a point', () => {
    const range = r('12+340', '12+340');
    expect(isPointRange(range)).toBe(true);
    expect(rangeLength(range)).toBe(0);
  });

  it('refuses a range that runs backwards', () => {
    expect(() => r('12+340', '12+000')).toThrow(BadInputError);
    expect(() => r('12+340', '12+000')).toThrow(/runs forwards/);
  });
});

describe('parseChainageRange', () => {
  it('reads the word form', () => {
    expect(parseChainageRange('12+000 to 12+340')).toEqual({
      from: 12_000_000,
      to: 12_340_000,
    });
  });

  it('reads the dash forms', () => {
    expect(parseChainageRange('12+000 -- 12+340').to).toBe(12_340_000);
    expect(parseChainageRange('12+000 – 12+340').to).toBe(12_340_000);
    expect(parseChainageRange('12+000 → 12+340').to).toBe(12_340_000);
  });

  it('treats a bare station as a point', () => {
    const range = parseChainageRange('12+340');
    expect(range.from).toBe(range.to);
    expect(range.from).toBe(12_340_000);
  });

  it('reports a bad station on either side', () => {
    const left = tryParseChainageRange('nope to 12+340');
    expect(left.ok).toBe(false);
    const right = tryParseChainageRange('12+000 to nope');
    expect(right.ok).toBe(false);
  });

  it('reports a range that runs backwards without throwing', () => {
    const back = tryParseChainageRange('12+340 to 12+000');
    expect(back.ok).toBe(false);
    if (!back.ok) expect(back.error.message).toMatch(/runs forwards/);
  });
});

describe('formatChainageRange', () => {
  it('writes both ends of a stretch', () => {
    expect(formatChainageRange(r('12+000', '12+340'))).toBe('12+000.000 to 12+340.000');
  });

  it('writes a point as a single station', () => {
    expect(formatChainageRange(r('12+340', '12+340'))).toBe('12+340.000');
  });
});

describe('rangeContains and rangeTouches', () => {
  const range = r('12+000', '12+340');

  it('includes the lower station and excludes the upper', () => {
    expect(rangeContains(range, at('12+000'))).toBe(true);
    expect(rangeContains(range, at('12+200'))).toBe(true);
    expect(rangeContains(range, at('12+340'))).toBe(false);
    expect(rangeContains(range, at('11+999'))).toBe(false);
  });

  it('touches both ends', () => {
    expect(rangeTouches(range, at('12+000'))).toBe(true);
    expect(rangeTouches(range, at('12+340'))).toBe(true);
    expect(rangeTouches(range, at('12+341'))).toBe(false);
  });
});

describe('rangesOverlap', () => {
  it('is false for stretches that only abut', () => {
    expect(rangesOverlap(r('12+000', '12+100'), r('12+100', '12+200'))).toBe(false);
  });

  it('is true when they share length', () => {
    expect(rangesOverlap(r('12+000', '12+150'), r('12+100', '12+200'))).toBe(true);
  });

  it('is false for a point on a boundary', () => {
    expect(rangesOverlap(r('12+000', '12+100'), r('12+100', '12+100'))).toBe(false);
  });

  it('is false for disjoint stretches', () => {
    expect(rangesOverlap(r('12+000', '12+100'), r('13+000', '13+100'))).toBe(false);
  });
});

describe('rangesEqual and rangeCovers', () => {
  it('compares both ends', () => {
    expect(rangesEqual(r('12+000', '12+100'), r('12+000', '12+100'))).toBe(true);
    expect(rangesEqual(r('12+000', '12+100'), r('12+000', '12+101'))).toBe(false);
  });

  it('covers an inner stretch including the shared ends', () => {
    expect(rangeCovers(r('12+000', '12+200'), r('12+050', '12+150'))).toBe(true);
    expect(rangeCovers(r('12+000', '12+200'), r('12+000', '12+200'))).toBe(true);
    expect(rangeCovers(r('12+000', '12+200'), r('12+150', '12+250'))).toBe(false);
  });
});

describe('intersectRanges', () => {
  it('returns the shared stretch', () => {
    expect(intersectRanges(r('12+000', '12+150'), r('12+100', '12+200'))).toEqual({
      from: 12_100_000,
      to: 12_150_000,
    });
  });

  it('returns null when they only abut', () => {
    expect(intersectRanges(r('12+000', '12+100'), r('12+100', '12+200'))).toBeNull();
  });

  it('returns null when they are apart', () => {
    expect(intersectRanges(r('12+000', '12+100'), r('14+000', '14+100'))).toBeNull();
  });
});

describe('subtractRange', () => {
  it('leaves the whole stretch when nothing overlaps', () => {
    expect(subtractRange(r('12+000', '12+100'), r('13+000', '13+100'))).toEqual([
      { from: 12_000_000, to: 12_100_000 },
    ]);
  });

  it('trims the front', () => {
    expect(subtractRange(r('12+000', '12+100'), r('11+900', '12+040'))).toEqual([
      { from: 12_040_000, to: 12_100_000 },
    ]);
  });

  it('trims the back', () => {
    expect(subtractRange(r('12+000', '12+100'), r('12+060', '12+200'))).toEqual([
      { from: 12_000_000, to: 12_060_000 },
    ]);
  });

  it('punches a hole and gives two pieces', () => {
    expect(subtractRange(r('12+000', '12+100'), r('12+040', '12+060'))).toEqual([
      { from: 12_000_000, to: 12_040_000 },
      { from: 12_060_000, to: 12_100_000 },
    ]);
  });

  it('gives nothing back when the cut swallows the stretch', () => {
    expect(subtractRange(r('12+000', '12+100'), r('11+000', '13+000'))).toEqual([]);
  });

  it('drops a point that sits inside the cut', () => {
    expect(subtractRange(r('12+050', '12+050'), r('12+000', '12+100'))).toEqual([]);
  });
});

describe('unionRanges', () => {
  it('sorts and merges overlapping stretches', () => {
    expect(unionRanges([r('12+100', '12+200'), r('12+000', '12+150')])).toEqual([
      { from: 12_000_000, to: 12_200_000 },
    ]);
  });

  it('merges stretches that only abut', () => {
    expect(unionRanges([r('12+000', '12+100'), r('12+100', '12+200')])).toEqual([
      { from: 12_000_000, to: 12_200_000 },
    ]);
  });

  it('keeps stretches that are genuinely apart', () => {
    expect(unionRanges([r('13+000', '13+100'), r('12+000', '12+100')])).toEqual([
      { from: 12_000_000, to: 12_100_000 },
      { from: 13_000_000, to: 13_100_000 },
    ]);
  });

  it('absorbs a point that lands inside a stretch', () => {
    expect(unionRanges([r('12+000', '12+100'), r('12+050', '12+050')])).toEqual([
      { from: 12_000_000, to: 12_100_000 },
    ]);
  });

  it('keeps a lone point', () => {
    expect(unionRanges([r('12+050', '12+050')])).toEqual([
      { from: 12_050_000, to: 12_050_000 },
    ]);
  });

  it('handles an empty list', () => {
    expect(unionRanges([])).toEqual([]);
  });

  it('does not mutate the input', () => {
    const input = [r('13+000', '13+100'), r('12+000', '12+100')];
    unionRanges(input);
    expect(input[0]?.from).toBe(13_000_000);
  });
});

describe('coveredLength', () => {
  it('counts overlap once', () => {
    expect(coveredLength([r('12+000', '12+150'), r('12+100', '12+200')])).toBe(200_000);
  });

  it('adds disjoint stretches', () => {
    expect(coveredLength([r('12+000', '12+100'), r('13+000', '13+050')])).toBe(150_000);
  });

  it('is zero for nothing at all', () => {
    expect(coveredLength([])).toBe(0);
  });
});

describe('splitRangeAt', () => {
  it('cuts in two', () => {
    expect(splitRangeAt(r('12+000', '12+100'), at('12+040'))).toEqual([
      { from: 12_000_000, to: 12_040_000 },
      { from: 12_040_000, to: 12_100_000 },
    ]);
  });

  it('leaves the stretch whole when the cut is at the start', () => {
    expect(splitRangeAt(r('12+000', '12+100'), at('12+000'))).toHaveLength(1);
  });

  it('leaves the stretch whole when the cut is outside', () => {
    expect(splitRangeAt(r('12+000', '12+100'), at('12+100'))).toHaveLength(1);
    expect(splitRangeAt(r('12+000', '12+100'), at('11+000'))).toHaveLength(1);
  });
});

describe('compareRanges', () => {
  it('orders by start then by end', () => {
    expect(compareRanges(r('12+000', '12+100'), r('12+050', '12+060'))).toBeLessThan(0);
    expect(compareRanges(r('12+000', '12+100'), r('12+000', '12+060'))).toBeGreaterThan(0);
    expect(compareRanges(r('12+000', '12+100'), r('12+000', '12+100'))).toBe(0);
  });
});
