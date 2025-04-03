import { describe, expect, it } from 'vitest';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import type {
  PaymentApplication,
  PaymentNotice,
} from '../../../../src/domain/subcontract/payment.js';
import { buildPaymentTerms } from '../../../../src/domain/subcontract/payment.js';
import {
  buildSubcontract,
  coversCode,
  coversPackage,
  describeSubcontract,
  findSubcontract,
  overOrder,
  requireSubcontract,
  subcontractPosition,
} from '../../../../src/domain/subcontract/subcontract.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');

const subcontract = buildSubcontract({
  id: 'SC-01',
  name: 'bulk earthworks and capping',
  contractor: 'Dowsett Plant Hire',
  orderValue: gbp(250_000),
  packageIds: ['WP-01', 'WP-02'],
  codes: [parseItemCode('E423.1'), parseItemCode('E532')],
  placedOn: d('2024-11-04'),
});

const applications: PaymentApplication[] = [
  {
    id: 'SA-01',
    subcontractId: 'SC-01',
    periodNumber: 1,
    appliedOn: d('2025-01-25'),
    gross: gbp(40_000),
    previouslyPaid: gbp(0),
  },
  {
    id: 'SA-02',
    subcontractId: 'SC-01',
    periodNumber: 2,
    appliedOn: d('2025-02-25'),
    gross: gbp(90_000),
    previouslyPaid: gbp(36_100),
  },
  {
    id: 'SA-03',
    subcontractId: 'SC-01',
    periodNumber: 3,
    appliedOn: d('2025-03-25'),
    gross: gbp(120_000),
    previouslyPaid: gbp(85_500),
  },
];

const notices: PaymentNotice[] = [
  { applicationId: 'SA-01', servedOn: d('2025-02-27'), valued: gbp(38_000) },
  { applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) },
];

describe('buildSubcontract', () => {
  it('keeps the packages and the priced items apart', () => {
    expect(subcontract.packageIds).toEqual(['WP-01', 'WP-02']);
    expect(subcontract.codes).toHaveLength(2);
  });

  it('falls back to the statutory payment terms', () => {
    expect(subcontract.terms.dueDays).toBe(30);
  });

  it('takes payment terms of its own', () => {
    const own = buildSubcontract({
      id: 'SC-02',
      name: 'drainage',
      contractor: 'Halliwell Civils',
      orderValue: gbp(80_000),
      terms: buildPaymentTerms({ dueDays: 21, retention: percent(3) }),
      placedOn: d('2024-12-02'),
    });
    expect(own.terms.dueDays).toBe(21);
    expect(own.packageIds).toEqual([]);
  });

  it('refuses a subcontract with no contractor named', () => {
    expect(() =>
      buildSubcontract({
        id: 'SC-03',
        name: 'fencing',
        contractor: '   ',
        orderValue: gbp(10_000),
        placedOn: d('2025-01-06'),
      }),
    ).toThrow(/name the subcontractor/);
  });

  it('refuses a negative order value', () => {
    expect(() =>
      buildSubcontract({
        id: 'SC-04',
        name: 'fencing',
        contractor: 'Halliwell Civils',
        orderValue: gbp(-1),
        placedOn: d('2025-01-06'),
      }),
    ).toThrow(/cannot be negative/);
  });

  it('refuses the same bill item twice on one order', () => {
    expect(() =>
      buildSubcontract({
        id: 'SC-05',
        name: 'fencing',
        contractor: 'Halliwell Civils',
        orderValue: gbp(10_000),
        codes: [parseItemCode('E423.1'), parseItemCode('E423.1')],
        placedOn: d('2025-01-06'),
      }),
    ).toThrow(/on one subcontract twice/);
  });
});

describe('what a subcontract covers', () => {
  it('answers on a package', () => {
    expect(coversPackage(subcontract, 'WP-01')).toBe(true);
    expect(coversPackage(subcontract, 'WP-09')).toBe(false);
  });

  it('answers on a bill item', () => {
    expect(coversCode(subcontract, parseItemCode('E532'))).toBe(true);
    expect(coversCode(subcontract, parseItemCode('E533'))).toBe(false);
  });
});

describe('subcontractPosition', () => {
  it('runs the applications in period order up to the date', () => {
    const position = subcontractPosition(
      subcontract,
      applications,
      notices,
      [],
      d('2025-03-25'),
    );
    expect(position.applications).toBe(3);
    expect(moneyMajor(position.certifiedToDate)).toBe(95_000);
    expect(moneyMajor(position.paidToDate)).toBe(95_000);
    expect(moneyMajor(position.remaining)).toBe(155_000);
  });

  it('names the application the payer let default', () => {
    const position = subcontractPosition(
      subcontract,
      applications,
      notices,
      [],
      d('2025-03-25'),
    );
    expect(position.defaulted).toEqual(['SA-02']);
  });

  it('leaves out an application made after the date', () => {
    const position = subcontractPosition(
      subcontract,
      applications,
      notices,
      [],
      d('2025-02-25'),
    );
    expect(position.applications).toBe(2);
    expect(moneyMajor(position.certifiedToDate)).toBe(85_500);
    expect(moneyMajor(position.remaining)).toBe(164_500);
  });

  it('ignores applications made against another order', () => {
    const other = { ...applications[0]!, id: 'SA-99', subcontractId: 'SC-02' };
    const position = subcontractPosition(
      subcontract,
      [...applications, other],
      notices,
      [],
      d('2025-03-25'),
    );
    expect(position.applications).toBe(3);
  });

  it('lets a pay less notice cut what is paid without moving what is certified', () => {
    const position = subcontractPosition(
      subcontract,
      applications,
      notices,
      [
        {
          applicationId: 'SA-03',
          servedOn: d('2025-05-30'),
          amount: gbp(2000),
          reason: 'capping laid outside tolerance at 12+500',
        },
      ],
      d('2025-03-25'),
    );
    expect(moneyMajor(position.certifiedToDate)).toBe(95_000);
    expect(moneyMajor(position.paidToDate)).toBe(87_500);
  });

  it('is empty on an order nobody has applied against', () => {
    const position = subcontractPosition(subcontract, [], [], [], d('2025-03-25'));
    expect(position.applications).toBe(0);
    expect(position.certifiedToDate.minor).toBe(0);
    expect(moneyMajor(position.remaining)).toBe(250_000);
  });
});

describe('overOrder', () => {
  it('picks out the order that has been certified past its value', () => {
    const small = buildSubcontract({
      id: 'SC-06',
      name: 'earthworks',
      contractor: 'Dowsett Plant Hire',
      orderValue: gbp(50_000),
      placedOn: d('2024-11-04'),
    });
    const positions = [
      subcontractPosition(subcontract, applications, notices, [], d('2025-03-25')),
      subcontractPosition(
        small,
        applications.map((a) => ({ ...a, subcontractId: 'SC-06' })),
        notices,
        [],
        d('2025-03-25'),
      ),
    ];
    expect(overOrder(positions).map((p) => p.subcontract.id)).toEqual(['SC-06']);
  });
});

describe('finding a subcontract', () => {
  it('returns null when there is none of that id', () => {
    expect(findSubcontract([subcontract], 'SC-99')).toBeNull();
  });

  it('throws when the caller needs one', () => {
    expect(() => requireSubcontract([subcontract], 'SC-99')).toThrow(/subcontract/);
    expect(requireSubcontract([subcontract], 'SC-01').contractor).toBe('Dowsett Plant Hire');
  });
});

describe('describeSubcontract', () => {
  it('says where the order stands in one line', () => {
    const position = subcontractPosition(
      subcontract,
      applications,
      notices,
      [],
      d('2025-03-25'),
    );
    expect(describeSubcontract(position)).toBe(
      'SC-01 Dowsett Plant Hire, 3 applications, inside the order',
    );
  });
});
