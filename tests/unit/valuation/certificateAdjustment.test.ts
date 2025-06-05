import { describe, expect, it } from 'vitest';
import { buildAdjustmentTerms } from '../../../src/domain/fluctuation/adjustment.js';
import { buildIndexSeries } from '../../../src/domain/fluctuation/index.js';
import { parseItemCode } from '../../../src/domain/bill/itemCode.js';
import { quantity } from '../../../src/domain/quantity/quantity.js';
import { moneyFromMajor, moneyMajor, zeroMoney } from '../../../src/domain/money/money.js';
import { percent } from '../../../src/domain/money/percent.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { parseSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildCertificate,
  certifySeries,
  provisionalCertificates,
  restsOnProvisionalIndex,
} from '../../../src/valuation/certificate.js';
import { buildMaterialsOnSite } from '../../../src/valuation/materials.js';
import { buildSchedule, periodByNumber } from '../../../src/valuation/period.js';
import { DEFAULT_RETENTION } from '../../../src/valuation/retention.js';
import { sampleContext } from '../../support/contextFixtures.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const context = sampleContext();
const contractSum = gbp(312_190);

const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});

const march = periodByNumber(schedule, 3);

const reading = (year: number, month: number, value: number, provisional = false) => ({
  year,
  month,
  value,
  provisional,
});

const labour = buildIndexSeries({
  id: 'IDX-LAB',
  name: 'civil engineering labour',
  readings: [
    reading(2024, 10, 100),
    reading(2025, 1, 105),
    reading(2025, 2, 110),
    reading(2025, 3, 120, true),
  ],
});

const terms = buildAdjustmentTerms({
  baseDate: d('2024-10-25'),
  nonAdjustable: percent(10),
  weightings: [{ indexId: 'IDX-LAB', weight: percent(90) }],
});

const base = {
  context,
  contractSum,
  retention: DEFAULT_RETENTION,
  previouslyCertified: zeroMoney('GBP'),
};

const plain = buildCertificate({ ...base, period: march });

const adjusted = buildCertificate({
  ...base,
  period: march,
  adjustment: terms,
  indices: [labour],
});

describe('a certificate with no fluctuation clause', () => {
  it('reports an adjustment of nothing', () => {
    expect(plain.priceAdjustment.amount.minor).toBe(0);
    expect(plain.priceAdjustment.factor.lines).toEqual([]);
  });

  it('rests on no index at all', () => {
    expect(restsOnProvisionalIndex(plain)).toBe(false);
  });

  it('grosses up to the measured work on its own', () => {
    expect(moneyMajor(plain.gross)).toBe(16_152.5);
  });
});

describe('a certificate with a fluctuation clause', () => {
  it('adjusts the permanent work by the factor', () => {
    // 20 percent movement, weighted 90 percent, on 90 percent of the work.
    expect(adjusted.priceAdjustment.factor.factor).toBeCloseTo(0.18, 10);
    expect(moneyMajor(adjusted.priceAdjustment.adjustable)).toBeCloseTo(14_537.25, 2);
    expect(moneyMajor(adjusted.priceAdjustment.amount)).toBeCloseTo(2616.71, 2);
  });

  it('carries the adjustment into the gross', () => {
    expect(moneyMajor(adjusted.gross)).toBeCloseTo(18_769.21, 2);
    expect(moneyMajor(adjusted.measuredWork)).toBe(16_152.5);
  });

  it('holds retention on the adjusted gross rather than the measured work', () => {
    expect(moneyMajor(adjusted.retention.held)).toBeGreaterThan(
      moneyMajor(plain.retention.held),
    );
  });

  it('says the certificate rests on a provisional figure', () => {
    expect(restsOnProvisionalIndex(adjusted)).toBe(true);
    expect(adjusted.priceAdjustment.provisional).toBe(true);
  });

  it('leaves materials on site out of the adjustment', () => {
    const withMaterials = buildCertificate({
      ...base,
      period: march,
      adjustment: terms,
      indices: [labour],
      materials: [
        buildMaterialsOnSite({
          id: 'MOS-01',
          packageId: 'WP-01',
          code: parseItemCode('E423.1'),
          description: 'imported capping stockpiled at the compound',
          quantity: quantity(400, 'm3'),
          value: gbp(8000),
          deliveredOn: d('2025-03-05'),
          vestedOn: d('2025-03-05'),
        }),
      ],
    });
    // The standard ninety percent allowance on materials on site.
    expect(moneyMajor(withMaterials.materialsOnSite)).toBe(7200);
    expect(moneyMajor(withMaterials.priceAdjustment.amount)).toBe(
      moneyMajor(adjusted.priceAdjustment.amount),
    );
    expect(moneyMajor(withMaterials.gross)).toBeCloseTo(moneyMajor(adjusted.gross) + 7200, 2);
  });
});

describe('a falling index', () => {
  const falling = buildIndexSeries({
    id: 'IDX-LAB',
    readings: [reading(2024, 10, 100), reading(2025, 3, 90)],
  });

  it('takes money off the certificate', () => {
    const down = buildCertificate({
      ...base,
      period: march,
      adjustment: terms,
      indices: [falling],
    });
    expect(down.priceAdjustment.amount.minor).toBeLessThan(0);
    expect(moneyMajor(down.gross)).toBeLessThan(moneyMajor(plain.gross));
  });
});

describe('a series with a fluctuation clause', () => {
  const series = certifySeries(schedule.periods.slice(0, 4), {
    context,
    contractSum,
    retention: DEFAULT_RETENTION,
    adjustment: terms,
    indices: [labour],
  });

  it('adjusts every certificate in the series', () => {
    expect(series).toHaveLength(4);
    expect(series[0]!.priceAdjustment.factor.factor).toBeCloseTo(0.045, 10);
    expect(series[2]!.priceAdjustment.factor.factor).toBeCloseTo(0.18, 10);
  });

  it('names the certificates that will have to be reworked', () => {
    expect(provisionalCertificates(series).map((c) => c.period.number)).toEqual([3, 4]);
  });

  it('still pays only the movement each month', () => {
    const cumulative = series.map((c) => moneyMajor(c.netValuation));
    const paid = series.map((c) => moneyMajor(c.amountDue));
    expect(paid.reduce((total, amount) => total + amount, 0)).toBeCloseTo(
      cumulative.at(-1)!,
      2,
    );
  });
});
