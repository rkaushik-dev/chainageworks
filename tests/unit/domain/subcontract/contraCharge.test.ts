import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import {
  applyContra,
  barredCharges,
  buildContraCharge,
  carriedForward,
  contraPosition,
  describeContra,
  isDeductibleAt,
} from '../../../../src/domain/subcontract/contraCharge.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const NOTIFY_DAYS = 14;

const attendance = buildContraCharge({
  id: 'CC-01',
  subcontractId: 'SC-EARTH',
  reference: 'CC/2025/001',
  reason: 'attendance',
  description: 'crane time and banksman, week 9',
  amount: gbp(1800),
  incurredOn: d('2025-02-28'),
});

const rectification = buildContraCharge({
  id: 'CC-02',
  subcontractId: 'SC-EARTH',
  reference: 'CC/2025/002',
  reason: 'rectification',
  description: 'capping relaid at 12+500 outside tolerance',
  amount: gbp(4200),
  incurredOn: d('2025-03-04'),
  notifiedOn: d('2025-03-10'),
});

const unnotified = buildContraCharge({
  id: 'CC-03',
  subcontractId: 'SC-EARTH',
  reference: 'CC/2025/003',
  reason: 'damage',
  description: 'kerb line struck by a dumper',
  amount: gbp(950),
  incurredOn: d('2025-03-01'),
});

const late = buildContraCharge({
  id: 'CC-04',
  subcontractId: 'SC-EARTH',
  reason: 'clean_up',
  description: 'compound cleared after the gang left',
  amount: gbp(600),
  incurredOn: d('2025-02-10'),
  notifiedOn: d('2025-03-20'),
});

const elsewhere = buildContraCharge({
  id: 'CC-05',
  subcontractId: 'SC-FENCE',
  reason: 'welfare',
  description: 'cabin share',
  amount: gbp(400),
  incurredOn: d('2025-03-02'),
});

const all = [attendance, rectification, unnotified, late, elsewhere];

describe('buildContraCharge', () => {
  it('takes the id as the reference and other as the reason', () => {
    const bare = buildContraCharge({
      id: 'CC-09',
      subcontractId: 'SC-EARTH',
      description: 'sundry',
      amount: gbp(100),
      incurredOn: d('2025-03-01'),
    });
    expect(bare.reference).toBe('CC-09');
    expect(bare.reason).toBe('other');
    expect(bare.notifiedOn).toBeNull();
    expect(bare.disputed).toBe(false);
  });

  it('refuses a negative charge', () => {
    expect(() =>
      buildContraCharge({
        id: 'CC-09',
        subcontractId: 'SC-EARTH',
        description: 'sundry',
        amount: gbp(-1),
        incurredOn: d('2025-03-01'),
      }),
    ).toThrow(/cannot be negative/);
  });

  it('refuses a charge that says nothing about what it is for', () => {
    expect(() =>
      buildContraCharge({
        id: 'CC-09',
        subcontractId: 'SC-EARTH',
        description: '  ',
        amount: gbp(100),
        incurredOn: d('2025-03-01'),
      }),
    ).toThrow(/what it is for/);
  });

  it('refuses a notice served before the cost was incurred', () => {
    expect(() =>
      buildContraCharge({
        id: 'CC-09',
        subcontractId: 'SC-EARTH',
        description: 'sundry',
        amount: gbp(100),
        incurredOn: d('2025-03-01'),
        notifiedOn: d('2025-02-28'),
      }),
    ).toThrow(/before it was incurred/);
  });
});

describe('isDeductibleAt', () => {
  it('lets an attendance charge stand with no notice at all', () => {
    expect(isDeductibleAt(attendance, d('2025-03-25'), NOTIFY_DAYS)).toBe(true);
  });

  it('lets a notified rectification charge stand', () => {
    expect(isDeductibleAt(rectification, d('2025-03-25'), NOTIFY_DAYS)).toBe(true);
  });

  it('refuses a rectification charge nobody notified', () => {
    expect(isDeductibleAt(unnotified, d('2025-03-25'), NOTIFY_DAYS)).toBe(false);
  });

  it('refuses a notice served outside the window', () => {
    expect(isDeductibleAt(late, d('2025-03-25'), NOTIFY_DAYS)).toBe(false);
  });

  it('takes a notice served on the last day of the window', () => {
    const onTime = buildContraCharge({
      ...late,
      id: 'CC-10',
      notifiedOn: d('2025-02-24'),
    });
    expect(isDeductibleAt(onTime, d('2025-03-25'), NOTIFY_DAYS)).toBe(true);
  });

  it('leaves a charge out until the day it was incurred', () => {
    expect(isDeductibleAt(rectification, d('2025-03-03'), NOTIFY_DAYS)).toBe(false);
  });

  it('leaves a charge out until the day it was notified', () => {
    expect(isDeductibleAt(rectification, d('2025-03-09'), NOTIFY_DAYS)).toBe(false);
    expect(isDeductibleAt(rectification, d('2025-03-10'), NOTIFY_DAYS)).toBe(true);
  });
});

describe('barredCharges', () => {
  it('names the charges whose window has shut unanswered', () => {
    expect(barredCharges(all, d('2025-03-25'), NOTIFY_DAYS).map((c) => c.id)).toEqual([
      'CC-03',
      'CC-04',
    ]);
  });

  it('names nothing while a window is still open', () => {
    expect(barredCharges([unnotified], d('2025-03-10'), NOTIFY_DAYS)).toEqual([]);
  });

  it('never bars a charge the order already covers', () => {
    expect(barredCharges([attendance], d('2025-06-30'), NOTIFY_DAYS)).toEqual([]);
  });
});

describe('contraPosition', () => {
  const position = contraPosition(all, 'SC-EARTH', d('2025-03-25'), NOTIFY_DAYS, 'GBP');

  it('takes only the charges against that order', () => {
    expect(position.charges.map((c) => c.id)).toEqual(['CC-04', 'CC-01', 'CC-03', 'CC-02']);
  });

  it('keeps what was raised apart from what may be taken', () => {
    expect(moneyMajor(position.raised)).toBe(7550);
    expect(moneyMajor(position.deductible)).toBe(6000);
    expect(moneyMajor(position.barred)).toBe(1550);
  });

  it('reports nothing disputed when nobody is arguing', () => {
    expect(position.disputed.minor).toBe(0);
  });

  it('reports the disputed part of what may be taken', () => {
    const arguing = contraPosition(
      [{ ...rectification, disputed: true }],
      'SC-EARTH',
      d('2025-03-25'),
      NOTIFY_DAYS,
      'GBP',
    );
    expect(moneyMajor(arguing.disputed)).toBe(4200);
    expect(moneyMajor(arguing.deductible)).toBe(4200);
  });

  it('is empty on an order with nothing charged against it', () => {
    const clean = contraPosition(all, 'SC-NONE', d('2025-03-25'), NOTIFY_DAYS, 'GBP');
    expect(clean.charges).toEqual([]);
    expect(clean.raised.minor).toBe(0);
  });
});

describe('applyContra', () => {
  const position = contraPosition(all, 'SC-EARTH', d('2025-03-25'), NOTIFY_DAYS, 'GBP');

  it('takes the deductible charges off the payment', () => {
    expect(moneyMajor(applyContra(gbp(20_000), position))).toBe(14_000);
  });

  it('never takes a payment below nothing', () => {
    expect(applyContra(gbp(1000), position).minor).toBe(0);
  });

  it('carries the rest into the next payment', () => {
    expect(moneyMajor(carriedForward(gbp(1000), position))).toBe(5000);
    expect(carriedForward(gbp(20_000), position).minor).toBe(0);
  });
});

describe('describeContra', () => {
  it('says the order, the count and what comes off', () => {
    const position = contraPosition(all, 'SC-EARTH', d('2025-03-25'), NOTIFY_DAYS, 'GBP');
    expect(describeContra(position)).toBe('SC-EARTH at 2025-03-25, 4 charges, 6000 deductible');
  });
});
