import { describe, expect, it } from 'vitest';
import {
  chainageRange,
  coveredLength,
  intersectRangeLists,
  rangeLength,
  subtractRange,
  unionRanges,
} from '../../src/domain/chainage/range.js';
import { asStation } from '../../src/domain/chainage/station.js';
import { apportionUnits } from '../../src/domain/money/apportion.js';
import { money, moneyFromMajor, subtractMoney } from '../../src/domain/money/money.js';
import { applyPercent, complementPercent, percent } from '../../src/domain/money/percent.js';
import { extend, rate } from '../../src/domain/money/rate.js';
import { quantity } from '../../src/domain/quantity/quantity.js';
import {
  addDays,
  diffDays,
  formatSiteDate,
  parseSiteDate,
} from '../../src/domain/time/siteDate.js';
import { rng, sample } from '../support/random.js';

const SEED = 8_301_457;

describe('apportionment always balances', () => {
  it('adds back to the total for a thousand random splits', () => {
    const random = rng(SEED);
    for (let run = 0; run < 1000; run += 1) {
      const total = random.int(-500_000, 500_000);
      const weights = sample(random.int(1, 8), () => random.int(0, 100));
      if (weights.every((w) => w === 0)) continue;
      const shares = apportionUnits(total, weights);
      expect(shares.reduce((a, b) => a + b, 0)).toBe(total);
      expect(shares).toHaveLength(weights.length);
    }
  });

  it('gives a bigger share to a bigger weight', () => {
    const random = rng(SEED + 1);
    for (let run = 0; run < 200; run += 1) {
      const total = random.int(1000, 100_000);
      const small = random.int(1, 40);
      const large = small + random.int(1, 40);
      const [a, b] = apportionUnits(total, [small, large]);
      expect(b!).toBeGreaterThanOrEqual(a!);
    }
  });
});

describe('chainage ranges', () => {
  const random = rng(SEED + 2);
  const someRange = () => {
    const from = random.int(0, 5000) * 1000;
    return chainageRange(asStation(from), asStation(from + random.int(0, 2000) * 1000));
  };

  it('never reports more covered length than the parts add to', () => {
    for (let run = 0; run < 500; run += 1) {
      const ranges = sample(random.int(1, 6), someRange);
      const naive = ranges.reduce((total, r) => total + rangeLength(r), 0);
      expect(coveredLength(ranges)).toBeLessThanOrEqual(naive);
    }
  });

  it('leaves a union sorted, disjoint and the same length as itself', () => {
    for (let run = 0; run < 500; run += 1) {
      const merged = unionRanges(sample(random.int(1, 6), someRange));
      for (let i = 1; i < merged.length; i += 1) {
        expect(merged[i]!.from).toBeGreaterThan(merged[i - 1]!.to);
      }
      expect(coveredLength(merged)).toBe(coveredLength([...merged, ...merged]));
    }
  });

  it('puts back what a subtraction took out', () => {
    for (let run = 0; run < 500; run += 1) {
      const whole = someRange();
      const cut = someRange();
      const left = subtractRange(whole, cut);
      const shared = intersectRangeLists([whole], [cut]);
      expect(coveredLength([...left, ...shared])).toBe(rangeLength(whole));
    }
  });

  it('never intersects to more than either side', () => {
    for (let run = 0; run < 500; run += 1) {
      const left = sample(random.int(1, 4), someRange);
      const right = sample(random.int(1, 4), someRange);
      const shared = coveredLength(intersectRangeLists(left, right));
      expect(shared).toBeLessThanOrEqual(coveredLength(left));
      expect(shared).toBeLessThanOrEqual(coveredLength(right));
    }
  });
});

describe('money and rates', () => {
  it('extends monotonically in the quantity', () => {
    const random = rng(SEED + 3);
    for (let run = 0; run < 500; run += 1) {
      const r = rate(random.int(1, 100_000) / 100, 'GBP', 'm3');
      const small = quantity(random.int(0, 5000) / 10, 'm3');
      const large = quantity(small.milli / 1000 + random.int(0, 5000) / 10, 'm3');
      expect(extend(r, large).minor).toBeGreaterThanOrEqual(extend(r, small).minor);
    }
  });

  it('never loses more than a penny either way on a percentage split', () => {
    const random = rng(SEED + 4);
    for (let run = 0; run < 500; run += 1) {
      const total = moneyFromMajor(random.int(0, 1_000_000) / 100, 'GBP');
      const share = percent(random.int(0, 10_000) / 100);
      const taken = applyPercent(total, share);
      const left = applyPercent(total, complementPercent(share));
      const drift = subtractMoney(total, money(taken.minor + left.minor, 'GBP'));
      expect(Math.abs(drift.minor)).toBeLessThanOrEqual(1);
    }
  });

  it('takes nothing at nought percent and everything at a hundred', () => {
    const random = rng(SEED + 5);
    for (let run = 0; run < 200; run += 1) {
      const total = moneyFromMajor(random.int(0, 1_000_000) / 100, 'GBP');
      expect(applyPercent(total, percent(0)).minor).toBe(0);
      expect(applyPercent(total, percent(100)).minor).toBe(total.minor);
    }
  });
});

describe('site dates', () => {
  it('round trips through the text form', () => {
    const random = rng(SEED + 6);
    const base = parseSiteDate('2000-01-01');
    for (let run = 0; run < 2000; run += 1) {
      const date = addDays(base, random.int(0, 20_000));
      expect(parseSiteDate(formatSiteDate(date))).toBe(date);
    }
  });

  it('steps forward and back to where it started', () => {
    const random = rng(SEED + 7);
    const base = parseSiteDate('2025-01-01');
    for (let run = 0; run < 1000; run += 1) {
      const step = random.int(-3000, 3000);
      expect(addDays(addDays(base, step), -step)).toBe(base);
      expect(diffDays(base, addDays(base, step))).toBe(step);
    }
  });
});
