import { describe, expect, it } from 'vitest';
import {
  buildAdjustmentTerms,
  priceAdjustment,
} from '../../src/domain/fluctuation/adjustment.js';
import { buildIndexSeries } from '../../src/domain/fluctuation/index.js';
import { moneyFromMajor } from '../../src/domain/money/money.js';
import { percent } from '../../src/domain/money/percent.js';
import type { PaymentApplication } from '../../src/domain/subcontract/payment.js';
import { buildPaymentTerms, paymentPosition } from '../../src/domain/subcontract/payment.js';
import {
  applyContra,
  buildContraCharge,
  carriedForward,
  contraPosition,
  isDeductibleAt,
} from '../../src/domain/subcontract/contraCharge.js';
import { addDays, parseSiteDate } from '../../src/domain/time/siteDate.js';
import { buildCashFlow } from '../../src/valuation/cashFlow.js';
import { buildSchedule } from '../../src/valuation/period.js';
import { buildCalendar } from '../../src/domain/time/calendar.js';
import { rng } from '../support/random.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const terms = buildPaymentTerms();

const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 12,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});

describe('a payment is never negative and never above the notified sum', () => {
  it('holds across a few hundred random applications and notices', () => {
    const random = rng(20250612);
    for (let i = 0; i < 400; i += 1) {
      const gross = random.int(0, 400_000);
      const previously = random.int(0, 400_000);
      const application: PaymentApplication = {
        id: `SA-${String(i)}`,
        subcontractId: 'SC-1',
        periodNumber: random.int(1, 12),
        appliedOn: addDays(d('2025-01-01'), random.int(0, 200)),
        gross: gbp(gross),
        previouslyPaid: gbp(previously),
      };
      const served = random.bool(0.6);
      const position = paymentPosition(
        application,
        terms,
        served
          ? {
              applicationId: application.id,
              servedOn: addDays(application.appliedOn, random.int(25, 45)),
              valued: gbp(random.int(0, 400_000)),
            }
          : null,
        null,
      );
      expect(position.payable.minor).toBeGreaterThanOrEqual(0);
      expect(position.payable.minor).toBeLessThanOrEqual(position.netOfRetention.minor);
      expect(position.retention.minor).toBeGreaterThanOrEqual(0);
      expect(position.byDefault).toBe(position.basis === 'application_by_default');
    }
  });
});

describe('a missed notice never lowers what is payable', () => {
  it('holds whenever the payer valued the work below the application', () => {
    const random = rng(97531);
    for (let i = 0; i < 200; i += 1) {
      const gross = random.int(1000, 400_000);
      const application: PaymentApplication = {
        id: `SA-${String(i)}`,
        subcontractId: 'SC-1',
        periodNumber: 1,
        appliedOn: d('2025-03-25'),
        gross: gbp(gross),
        previouslyPaid: gbp(0),
      };
      const lower = gbp(random.int(0, gross));
      const inTime = paymentPosition(
        application,
        terms,
        { applicationId: application.id, servedOn: d('2025-04-29'), valued: lower },
        null,
      );
      const late = paymentPosition(
        application,
        terms,
        { applicationId: application.id, servedOn: d('2025-04-30'), valued: lower },
        null,
      );
      expect(late.payable.minor).toBeGreaterThanOrEqual(inTime.payable.minor);
    }
  });
});

describe('set off never takes a payment below nothing and never loses money', () => {
  it('holds across random charges against random payments', () => {
    const random = rng(11223344);
    for (let i = 0; i < 300; i += 1) {
      const charges = Array.from({ length: random.int(0, 5) }, (_, n) => {
        const incurredOn = addDays(d('2025-01-01'), random.int(0, 60));
        return buildContraCharge({
          id: `CC-${String(i)}-${String(n)}`,
          subcontractId: 'SC-1',
          reason: random.pick(['attendance', 'welfare', 'rectification', 'damage'] as const),
          description: 'a charge',
          amount: gbp(random.int(0, 20_000)),
          incurredOn,
          ...(random.bool(0.6) ? { notifiedOn: addDays(incurredOn, random.int(0, 40)) } : {}),
        });
      });
      const asAt = addDays(d('2025-01-01'), random.int(0, 120));
      const position = contraPosition(charges, 'SC-1', asAt, 14, 'GBP');
      const payable = gbp(random.int(0, 60_000));

      expect(position.deductible.minor).toBeLessThanOrEqual(position.raised.minor);
      expect(position.disputed.minor).toBeLessThanOrEqual(position.deductible.minor);
      expect(applyContra(payable, position).minor).toBeGreaterThanOrEqual(0);
      // Whatever is paid, less whatever waits for next time, is always the
      // payment less the set off. Nothing is lost between the two.
      expect(
        applyContra(payable, position).minor - carriedForward(payable, position).minor,
      ).toBe(payable.minor - position.deductible.minor);
    }
  });

  it('never deducts a charge that was incurred after the date', () => {
    const random = rng(5150);
    for (let i = 0; i < 200; i += 1) {
      const incurred = addDays(d('2025-01-01'), random.int(0, 120));
      const charge = buildContraCharge({
        id: `CC-${String(i)}`,
        subcontractId: 'SC-1',
        reason: 'attendance',
        description: 'a charge',
        amount: gbp(random.int(1, 5000)),
        incurredOn: incurred,
      });
      const before = addDays(incurred, -random.int(1, 30));
      expect(isDeductibleAt(charge, before, 14)).toBe(false);
      expect(isDeductibleAt(charge, incurred, 14)).toBe(true);
    }
  });
});

describe('the cash forecast never invents money', () => {
  it('never receives more than has been certified, on a series that only rises', () => {
    const random = rng(60606);
    for (let i = 0; i < 200; i += 1) {
      const late = random.int(0, 3);
      let running = 0;
      const certified = schedule.periods.map(() => {
        running += random.int(0, 40_000);
        return gbp(running);
      });
      const points = buildCashFlow({
        periods: schedule.periods,
        certified,
        currency: 'GBP',
        latePeriods: late,
      });

      for (const point of points) {
        expect(point.received.minor).toBeLessThanOrEqual(point.certified.minor);
        expect(point.outstanding.minor).toBeGreaterThanOrEqual(0);
        expect(point.receipt.minor).toBeGreaterThanOrEqual(0);
      }
    }
  });

  it('still balances when a remeasure takes the certified figure back down', () => {
    const random = rng(70707);
    for (let i = 0; i < 200; i += 1) {
      const late = random.int(0, 3);
      let running = 0;
      const certified = schedule.periods.map(() => {
        running = Math.max(0, running + random.int(-20_000, 40_000));
        return gbp(running);
      });
      const points = buildCashFlow({
        periods: schedule.periods,
        certified,
        currency: 'GBP',
        latePeriods: late,
      });

      for (const [index, point] of points.entries()) {
        expect(point.outstanding.minor).toBe(point.certified.minor - point.received.minor);
        expect(point.received.minor).toBe(index < late ? 0 : certified[index - late]!.minor);
      }
      // The last receipt lands on whatever was certified `late` periods back,
      // so a job that stops moving still ends up square.
      expect(points.at(-1)!.received.minor).toBe(
        certified[schedule.periods.length - 1 - late]!.minor,
      );
    }
  });
});

describe('the price adjustment moves with the index and never touches the fixed part', () => {
  it('holds for any base, any movement and any fixed share', () => {
    const random = rng(424242);
    for (let i = 0; i < 300; i += 1) {
      const base = random.int(80, 130);
      const later = random.int(80, 200);
      const fixed = random.int(0, 100);
      const series = buildIndexSeries({
        id: 'IDX',
        readings: [
          { year: 2024, month: 10, value: base, provisional: false },
          { year: 2025, month: 3, value: later, provisional: random.bool() },
        ],
      });
      const clause = buildAdjustmentTerms({
        baseDate: d('2024-10-25'),
        nonAdjustable: percent(fixed),
        weightings: [{ indexId: 'IDX', weight: percent(100 - fixed) }],
      });
      const value = gbp(random.int(0, 500_000));
      const adjustment = priceAdjustment(clause, [series], value, d('2025-03-25'));

      expect(adjustment.adjustable.minor).toBeLessThanOrEqual(value.minor);
      if (later >= base) expect(adjustment.amount.minor).toBeGreaterThanOrEqual(0);
      if (later <= base) expect(adjustment.amount.minor).toBeLessThanOrEqual(0);
      if (fixed === 100) expect(adjustment.amount.minor).toBe(0);
      expect(priceAdjustment(clause, [series], value, d('2024-10-25')).amount.minor).toBe(0);
    }
  });
});
