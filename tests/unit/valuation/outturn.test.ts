import { describe, expect, it } from 'vitest';
import { parseItemCode } from '../../../src/domain/bill/itemCode.js';
import { buildCompletionTerms } from '../../../src/domain/delay/completion.js';
import { buildDelayEvent } from '../../../src/domain/delay/event.js';
import { buildAdjustmentTerms } from '../../../src/domain/fluctuation/adjustment.js';
import { buildIndexSeries } from '../../../src/domain/fluctuation/index.js';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { percent, percentValue } from '../../../src/domain/money/percent.js';
import { quantity } from '../../../src/domain/quantity/quantity.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { parseSiteDate } from '../../../src/domain/time/siteDate.js';
import { buildVariation } from '../../../src/domain/variation/variation.js';
import {
  buildOutturn,
  describeOutturn,
  forecastDaysLate,
  isOverContractSum,
  outturnParts,
  partsAddUp,
} from '../../../src/valuation/outturn.js';
import { sampleContext } from '../../support/contextFixtures.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const context = sampleContext();
const contractSum = gbp(312_190);
const calendar = buildCalendar({ id: 'C1', name: 'main works' });
const asAt = d('2025-03-25');

const base = { context, asAt, contractSum };

const variation = buildVariation({
  id: 'VO-01',
  reference: 'VO/2025/001',
  title: 'extra rock excavation at the cutting',
  status: 'instructed',
  instructedOn: d('2025-02-10'),
  items: [
    {
      code: parseItemCode('E423.1'),
      quantityChange: quantity(500, 'm3'),
      rate: null,
      basis: 'bill_rates',
    },
  ],
});

const labour = buildIndexSeries({
  id: 'IDX-LAB',
  readings: [
    { year: 2024, month: 10, value: 100, provisional: false },
    { year: 2025, month: 3, value: 110, provisional: false },
  ],
});

const clause = buildAdjustmentTerms({
  baseDate: d('2024-10-25'),
  nonAdjustable: percent(10),
  weightings: [{ indexId: 'IDX-LAB', weight: percent(90) }],
});

const completion = buildCompletionTerms({
  baseCompletion: d('2025-02-14'),
  damagesPerDay: gbp(2500),
  damagesCap: percent(10),
});

describe('a bare outturn', () => {
  const outturn = buildOutturn(base);

  it('is the measured works and nothing else', () => {
    expect(moneyMajor(outturn.measuredWorks)).toBe(moneyMajor(outturn.account.forecastOutturn));
    expect(outturn.variations.minor).toBe(0);
    expect(outturn.daywork.minor).toBe(0);
    expect(outturn.damages.minor).toBe(0);
    expect(outturn.priceAdjustment.amount.minor).toBe(0);
  });

  it('totals to the measured works', () => {
    expect(moneyMajor(outturn.total)).toBe(moneyMajor(outturn.measuredWorks));
  });

  it('measures the movement against the contract sum', () => {
    expect(moneyMajor(outturn.movement)).toBe(
      moneyMajor(outturn.total) - moneyMajor(contractSum),
    );
    expect(percentValue(outturn.movementPercent)).toBeLessThanOrEqual(0);
    expect(isOverContractSum(outturn)).toBe(false);
  });

  it('forecasts a job with nothing measured against it at the bill total', () => {
    expect(moneyMajor(outturn.total)).toBe(moneyMajor(contractSum));
    expect(outturn.movement.minor).toBe(0);
  });

  it('adds up from its own parts', () => {
    expect(partsAddUp(outturn)).toBe(true);
  });
});

describe('an outturn with variations', () => {
  const outturn = buildOutturn({ ...base, variations: [variation] });

  it('values the variation against the unvaried bill', () => {
    expect(outturn.variations.minor).toBeGreaterThan(0);
    expect(moneyMajor(outturn.variations)).toBe(2425);
  });

  it('carries the variation into the total', () => {
    const bare = buildOutturn(base);
    expect(moneyMajor(outturn.total)).toBe(
      moneyMajor(bare.total) + moneyMajor(outturn.variations),
    );
  });

  it('ignores a variation that has not been instructed', () => {
    const proposed = buildOutturn({
      ...base,
      variations: [buildVariation({ ...variation, status: 'proposed' })],
    });
    expect(proposed.variations.minor).toBe(0);
  });
});

describe('an outturn with a fluctuation clause', () => {
  const outturn = buildOutturn({ ...base, adjustment: clause, indices: [labour] });

  it('applies the factor to the forecast permanent work', () => {
    expect(outturn.priceAdjustment.factor.factor).toBeCloseTo(0.09, 10);
    expect(outturn.priceAdjustment.amount.minor).toBeGreaterThan(0);
  });

  it('takes the fixed element off before applying it', () => {
    expect(moneyMajor(outturn.priceAdjustment.adjustable)).toBeCloseTo(
      moneyMajor(outturn.measuredWorks) * 0.9,
      1,
    );
  });

  it('still adds up from its parts', () => {
    expect(partsAddUp(outturn)).toBe(true);
  });
});

describe('an outturn on a late job', () => {
  const outturn = buildOutturn({
    ...base,
    completion,
    calendar,
    delayEvents: [
      buildDelayEvent({
        id: 'DE-01',
        reference: 'EW/2025/001',
        cause: 'utilities',
        description: 'live water main',
        from: d('2025-01-06'),
        to: d('2025-01-10'),
        notifiedOn: d('2025-01-07'),
        awardedDays: 5,
      }),
    ],
  });

  it('deducts the damages from the total', () => {
    expect(outturn.damages.minor).toBeGreaterThan(0);
    const bare = buildOutturn(base);
    expect(moneyMajor(outturn.total)).toBe(
      moneyMajor(bare.total) - moneyMajor(outturn.damages),
    );
  });

  it('reports how late the job is forecast to be', () => {
    expect(
      forecastDaysLate({ ...base, completion, calendar, delayEvents: [] }),
    ).toBeGreaterThan(0);
  });

  it('is not late at all when the contract fixes no completion date', () => {
    expect(forecastDaysLate(base)).toBe(0);
    expect(buildOutturn(base).damages.minor).toBe(0);
  });

  it('still adds up from its parts', () => {
    expect(partsAddUp(outturn)).toBe(true);
  });
});

describe('outturnParts', () => {
  it('sets the account out the way a final account letter does', () => {
    const outturn = buildOutturn({ ...base, variations: [variation] });
    expect(outturnParts(outturn).map((part) => part.name)).toEqual([
      'measured works',
      'variations',
      'daywork',
      'price adjustment',
      'liquidated damages',
    ]);
  });

  it('carries the damages as a deduction', () => {
    const outturn = buildOutturn({ ...base, completion, calendar });
    const damages = outturnParts(outturn).at(-1)!;
    expect(damages.amount.minor).toBeLessThan(0);
  });
});

describe('describeOutturn', () => {
  it('says which side of the contract sum the job is on', () => {
    expect(describeOutturn(buildOutturn(base))).toMatch(
      /^outturn at 2025-03-25, [\d.]+ under the contract sum$/,
    );
  });
});
