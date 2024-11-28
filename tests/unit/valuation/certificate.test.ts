import { describe, expect, it } from 'vitest';
import { parseItemCode } from '../../../src/domain/bill/itemCode.js';
import { moneyFromMajor, moneyMajor, zeroMoney } from '../../../src/domain/money/money.js';
import { percent } from '../../../src/domain/money/percent.js';
import { quantity } from '../../../src/domain/quantity/quantity.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { parseSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  buildCertificate,
  certifiedToDate,
  certifySeries,
  isNegativeCertificate,
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

const certificate = buildCertificate({
  context,
  period: march,
  contractSum,
  retention: DEFAULT_RETENTION,
  previouslyCertified: zeroMoney('GBP'),
});

describe('buildCertificate', () => {
  it('values the measured work at the cut off', () => {
    expect(moneyMajor(certificate.measuredWork)).toBe(16_152.5);
  });

  it('names the bill revision it was priced against', () => {
    expect(certificate.billRevision).toBe('Rev A');
  });

  it('carries nothing for materials when none were declared', () => {
    expect(certificate.materialsOnSite.minor).toBe(0);
    expect(certificate.materialsClaimed).toEqual([]);
  });

  it('holds retention off the gross', () => {
    expect(moneyMajor(certificate.gross)).toBe(16_152.5);
    expect(moneyMajor(certificate.retention.held)).toBe(807.63);
    expect(moneyMajor(certificate.netValuation)).toBe(15_344.87);
  });

  it('pays the whole net figure on the first certificate', () => {
    expect(moneyMajor(certificate.amountDue)).toBe(15_344.87);
    expect(isNegativeCertificate(certificate)).toBe(false);
  });

  it('carries the package detail through for the backing sheets', () => {
    expect(certificate.earnings.map((e) => e.packageId)).toEqual(['WP-01', 'WP-03', 'WP-02']);
  });

  it('refuses a previous certificate in another currency', () => {
    expect(() =>
      buildCertificate({
        context,
        period: march,
        contractSum,
        retention: DEFAULT_RETENTION,
        previouslyCertified: zeroMoney('EUR'),
      }),
    ).toThrow(/another currency/);
  });
});

describe('materials on the certificate', () => {
  const pipes = buildMaterialsOnSite({
    id: 'MOS-01',
    packageId: 'WP-02',
    code: parseItemCode('E423.1'),
    description: 'imported fill stockpiled at the compound',
    quantity: quantity(1200, 'm3'),
    value: gbp(9000),
    deliveredOn: d('2025-03-10'),
    vestedOn: d('2025-03-12'),
  });

  it('adds the allowed value to the gross', () => {
    const withMaterials = buildCertificate({
      context,
      period: march,
      contractSum,
      retention: DEFAULT_RETENTION,
      materials: [pipes],
      previouslyCertified: zeroMoney('GBP'),
    });
    expect(moneyMajor(withMaterials.materialsOnSite)).toBe(8100);
    expect(moneyMajor(withMaterials.gross)).toBe(16_152.5 + 8100);
    expect(withMaterials.materialsClaimed.map((m) => m.id)).toEqual(['MOS-01']);
  });

  it('honours a different allowance', () => {
    const full = buildCertificate({
      context,
      period: march,
      contractSum,
      retention: DEFAULT_RETENTION,
      materials: [pipes],
      materialsAllowance: percent(100),
      previouslyCertified: zeroMoney('GBP'),
    });
    expect(moneyMajor(full.materialsOnSite)).toBe(9000);
  });

  it('drops them once they are built in', () => {
    const built = buildMaterialsOnSite({
      id: 'MOS-01',
      packageId: 'WP-02',
      code: parseItemCode('E423.1'),
      description: 'imported fill stockpiled at the compound',
      quantity: quantity(1200, 'm3'),
      value: gbp(9000),
      deliveredOn: d('2025-03-10'),
      vestedOn: d('2025-03-12'),
      incorporatedOn: d('2025-03-20'),
    });
    const after = buildCertificate({
      context,
      period: march,
      contractSum,
      retention: DEFAULT_RETENTION,
      materials: [built],
      previouslyCertified: zeroMoney('GBP'),
    });
    expect(after.materialsOnSite.minor).toBe(0);
  });
});

describe('the amount due is a difference', () => {
  it('pays only the movement when work was certified before', () => {
    const second = buildCertificate({
      context,
      period: march,
      contractSum,
      retention: DEFAULT_RETENTION,
      previouslyCertified: gbp(10_000),
    });
    expect(moneyMajor(second.amountDue)).toBe(5344.87);
    expect(moneyMajor(certifiedToDate(second))).toBe(15_344.87);
  });

  it('goes negative when this month values less than the last', () => {
    const overpaid = buildCertificate({
      context,
      period: march,
      contractSum,
      retention: DEFAULT_RETENTION,
      previouslyCertified: gbp(20_000),
    });
    expect(isNegativeCertificate(overpaid)).toBe(true);
    expect(moneyMajor(overpaid.amountDue)).toBe(-4655.13);
  });
});

describe('certifySeries', () => {
  const series = certifySeries(schedule.periods.slice(0, 4), {
    context,
    contractSum,
    retention: DEFAULT_RETENTION,
  });

  it('runs one certificate per period', () => {
    expect(series.map((c) => c.period.number)).toEqual([1, 2, 3, 4]);
  });

  it('feeds each one the last net figure', () => {
    expect(series[0]?.previouslyCertified.minor).toBe(0);
    expect(series[3]?.previouslyCertified).toEqual(series[2]?.netValuation);
  });

  it('pays nothing in the months before anything was built', () => {
    expect(series[0]?.amountDue.minor).toBe(0);
    expect(series[1]?.amountDue.minor).toBe(0);
  });

  it('pays the movement in the month the work landed', () => {
    expect(moneyMajor(series[2]!.amountDue)).toBe(15_344.87);
  });

  it('adds up to the last net valuation', () => {
    const paid = series.reduce((total, c) => total + c.amountDue.minor, 0);
    expect(paid).toBe(series[3]?.netValuation.minor);
  });
});
