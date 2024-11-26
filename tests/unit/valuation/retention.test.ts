import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { percent, percentValue } from '../../../src/domain/money/percent.js';
import { parseSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildRetentionTerms,
  computeRetention,
  DEFAULT_RETENTION,
  effectiveRate,
  outstandingRelease,
  payableShare,
} from '../../../src/valuation/retention.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const contractSum = gbp(2_000_000);

const at = (gross: number, extra: Partial<Parameters<typeof computeRetention>[0]> = {}) =>
  computeRetention({
    grossValuation: gbp(gross),
    contractSum,
    terms: DEFAULT_RETENTION,
    asAt: d('2025-03-31'),
    ...extra,
  });

describe('the default terms', () => {
  it('holds five percent up to three percent of the contract sum', () => {
    expect(percentValue(DEFAULT_RETENTION.rate)).toBe(5);
    expect(percentValue(DEFAULT_RETENTION.limit)).toBe(3);
    expect(percentValue(DEFAULT_RETENTION.releaseOnCompletion)).toBe(50);
  });

  it('pays across the rest of every valuation', () => {
    expect(percentValue(payableShare(DEFAULT_RETENTION))).toBe(95);
  });

  it('refuses a percentage outside nought to a hundred', () => {
    expect(() => buildRetentionTerms({ ...DEFAULT_RETENTION, rate: percent(-1) })).toThrow(
      /runs from 0 to 100/,
    );
    expect(() => buildRetentionTerms({ ...DEFAULT_RETENTION, limit: percent(101) })).toThrow(
      /runs from 0 to 100/,
    );
    expect(() =>
      buildRetentionTerms({ ...DEFAULT_RETENTION, releaseOnCompletion: percent(150) }),
    ).toThrow(/runs from 0 to 100/);
  });

  it('accepts the defaults', () => {
    expect(buildRetentionTerms(DEFAULT_RETENTION)).toBe(DEFAULT_RETENTION);
  });
});

describe('accruing retention', () => {
  it('holds the rate while the total is under the cap', () => {
    const state = at(400_000);
    expect(moneyMajor(state.uncapped)).toBe(20_000);
    expect(moneyMajor(state.cap)).toBe(60_000);
    expect(moneyMajor(state.accrued)).toBe(20_000);
    expect(state.capReached).toBe(false);
  });

  it('stops at the cap however far the works get', () => {
    const state = at(1_800_000);
    expect(moneyMajor(state.uncapped)).toBe(90_000);
    expect(moneyMajor(state.accrued)).toBe(60_000);
    expect(state.capReached).toBe(true);
  });

  it('holds exactly the cap at the crossover', () => {
    const state = at(1_200_000);
    expect(moneyMajor(state.accrued)).toBe(60_000);
    expect(state.capReached).toBe(false);
  });

  it('holds nothing against nothing', () => {
    const state = at(0);
    expect(state.accrued.minor).toBe(0);
    expect(state.held.minor).toBe(0);
  });
});

describe('releasing retention', () => {
  const completion = d('2025-02-15');
  const defects = d('2026-02-15');

  it('holds the lot before the works are taken over', () => {
    const state = at(400_000, { substantialCompletionOn: completion, asAt: d('2025-01-31') });
    expect(moneyMajor(state.held)).toBe(20_000);
    expect(state.releasedOnCompletion.minor).toBe(0);
  });

  it('releases half at substantial completion', () => {
    const state = at(400_000, { substantialCompletionOn: completion });
    expect(moneyMajor(state.releasedOnCompletion)).toBe(10_000);
    expect(moneyMajor(state.held)).toBe(10_000);
  });

  it('does not release the rest until the defects certificate', () => {
    const state = at(400_000, {
      substantialCompletionOn: completion,
      defectsCertificateOn: defects,
      asAt: d('2025-12-31'),
    });
    expect(moneyMajor(state.held)).toBe(10_000);
    expect(state.releasedOnDefects.minor).toBe(0);
  });

  it('releases everything once the defects certificate is issued', () => {
    const state = at(400_000, {
      substantialCompletionOn: completion,
      defectsCertificateOn: defects,
      asAt: d('2026-03-31'),
    });
    expect(moneyMajor(state.releasedOnCompletion)).toBe(10_000);
    expect(moneyMajor(state.releasedOnDefects)).toBe(10_000);
    expect(state.held.minor).toBe(0);
    expect(outstandingRelease(state).minor).toBe(0);
  });

  it('releases the whole lot at the defects certificate when nothing was released before', () => {
    const state = at(400_000, {
      defectsCertificateOn: defects,
      asAt: d('2026-03-31'),
    });
    expect(moneyMajor(state.releasedOnDefects)).toBe(20_000);
    expect(state.held.minor).toBe(0);
  });

  it('releases against the capped total, not the uncapped one', () => {
    const state = at(1_800_000, { substantialCompletionOn: completion });
    expect(moneyMajor(state.releasedOnCompletion)).toBe(30_000);
    expect(moneyMajor(state.held)).toBe(30_000);
  });
});

describe('effectiveRate', () => {
  it('is the contract rate while the cap is clear', () => {
    const state = at(400_000);
    expect(percentValue(effectiveRate(state, gbp(400_000)))).toBe(5);
  });

  it('falls away once the cap bites', () => {
    const state = at(1_800_000);
    expect(percentValue(effectiveRate(state, gbp(1_800_000)))).toBeCloseTo(3.33, 2);
  });

  it('is nothing against a nil valuation', () => {
    expect(percentValue(effectiveRate(at(0), gbp(0)))).toBe(0);
  });
});
