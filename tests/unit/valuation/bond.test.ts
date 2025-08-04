import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { percent } from '../../../src/domain/money/percent.js';
import { parseSiteDate } from '../../../src/domain/time/siteDate.js';
import {
  annualCharge,
  bondPosition,
  buildRetentionBond,
  DEFAULT_BOND_RATE,
  describeBond,
  expiringWithin,
  isWorthTaking,
  totalCharge,
} from '../../../src/valuation/bond.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');

const bond = buildRetentionBond({
  id: 'RB-01',
  surety: 'Northgate Surety',
  maximum: gbp(9365),
  issuedOn: d('2024-11-01'),
  expiresOn: d('2026-11-01'),
  rate: percent(1.2),
  reducesTo: gbp(4682),
});

describe('buildRetentionBond', () => {
  it('takes the standard rate when none is given', () => {
    const bare = buildRetentionBond({
      id: 'RB-02',
      surety: 'Northgate Surety',
      maximum: gbp(10_000),
      issuedOn: d('2025-01-01'),
      expiresOn: d('2026-01-01'),
    });
    expect(bare.rate).toEqual(DEFAULT_BOND_RATE);
    expect(bare.reducesTo).toBeNull();
  });

  it('refuses a bond for nothing', () => {
    expect(() =>
      buildRetentionBond({
        id: 'RB-03',
        surety: 'Northgate Surety',
        maximum: gbp(0),
        issuedOn: d('2025-01-01'),
        expiresOn: d('2026-01-01'),
      }),
    ).toThrow(/for some amount/);
  });

  it('refuses a bond that expires before it is issued', () => {
    expect(() =>
      buildRetentionBond({
        id: 'RB-04',
        surety: 'Northgate Surety',
        maximum: gbp(10_000),
        issuedOn: d('2026-01-01'),
        expiresOn: d('2025-01-01'),
      }),
    ).toThrow(/expire before it is issued/);
  });

  it('refuses a reduction larger than the bond itself', () => {
    expect(() =>
      buildRetentionBond({
        id: 'RB-05',
        surety: 'Northgate Surety',
        maximum: gbp(10_000),
        issuedOn: d('2025-01-01'),
        expiresOn: d('2026-01-01'),
        reducesTo: gbp(12_000),
      }),
    ).toThrow(/reduce to more than it started at/);
  });

  it('refuses a rate outside nought to a hundred', () => {
    expect(() =>
      buildRetentionBond({
        id: 'RB-06',
        surety: 'Northgate Surety',
        maximum: gbp(10_000),
        issuedOn: d('2025-01-01'),
        expiresOn: d('2026-01-01'),
        rate: percent(140),
      }),
    ).toThrow(/runs from 0 to 100/);
  });
});

describe('bondPosition', () => {
  it('covers the retention in full while the bond is big enough', () => {
    const position = bondPosition(bond, gbp(5000), d('2025-03-25'));
    expect(moneyMajor(position.cover)).toBe(9365);
    expect(moneyMajor(position.released)).toBe(5000);
    expect(position.shortfall.minor).toBe(0);
    expect(position.expired).toBe(false);
  });

  it('reports the part of the retention the bond cannot reach', () => {
    const position = bondPosition(bond, gbp(12_000), d('2025-03-25'));
    expect(moneyMajor(position.released)).toBe(9365);
    expect(moneyMajor(position.shortfall)).toBe(2635);
  });

  it('covers nothing before the bond was issued', () => {
    const position = bondPosition(bond, gbp(5000), d('2024-10-01'));
    expect(position.cover.minor).toBe(0);
    expect(moneyMajor(position.shortfall)).toBe(5000);
  });

  it('covers nothing once the bond has lapsed', () => {
    const position = bondPosition(bond, gbp(5000), d('2026-11-02'));
    expect(position.expired).toBe(true);
    expect(position.cover.minor).toBe(0);
    expect(moneyMajor(position.shortfall)).toBe(5000);
  });

  it('drops to the reduced amount from substantial completion', () => {
    const position = bondPosition(bond, gbp(9000), d('2025-10-01'), d('2025-09-26'));
    expect(moneyMajor(position.cover)).toBe(4682);
    expect(moneyMajor(position.shortfall)).toBe(4318);
  });

  it('holds the full amount up to the day before completion', () => {
    const position = bondPosition(bond, gbp(9000), d('2025-09-25'), d('2025-09-26'));
    expect(moneyMajor(position.cover)).toBe(9365);
  });

  it('counts the days to expiry', () => {
    expect(bondPosition(bond, gbp(5000), d('2026-10-25')).daysToExpiry).toBe(7);
  });
});

describe('what a bond costs', () => {
  it('charges on the face rather than on what is drawn', () => {
    expect(moneyMajor(annualCharge(bond))).toBeCloseTo(112.38, 2);
  });

  it('charges for the whole life of the bond', () => {
    expect(moneyMajor(totalCharge(bond))).toBeCloseTo(224.76 * (730 / 730), 1);
  });

  it('charges nothing at a rate of nothing', () => {
    const free = buildRetentionBond({
      id: 'RB-07',
      surety: 'Northgate Surety',
      maximum: gbp(10_000),
      issuedOn: d('2025-01-01'),
      expiresOn: d('2026-01-01'),
      rate: percent(0),
    });
    expect(totalCharge(free).minor).toBe(0);
  });
});

describe('isWorthTaking', () => {
  it('is worth it when money costs more than the surety does', () => {
    expect(isWorthTaking(bond, gbp(9365), percent(6))).toBe(true);
  });

  it('is not worth it when the retention it would free up is small', () => {
    expect(isWorthTaking(bond, gbp(500), percent(2))).toBe(false);
  });
});

describe('expiringWithin', () => {
  const soon = buildRetentionBond({
    id: 'RB-08',
    surety: 'Northgate Surety',
    maximum: gbp(5000),
    issuedOn: d('2024-01-01'),
    expiresOn: d('2025-04-10'),
  });

  it('names the bonds about to lapse, soonest first', () => {
    expect(expiringWithin([bond, soon], d('2025-03-25'), 30).map((b) => b.id)).toEqual([
      'RB-08',
    ]);
  });

  it('leaves out one that has already lapsed', () => {
    expect(expiringWithin([soon], d('2025-05-01'), 30)).toEqual([]);
  });

  it('leaves out one nowhere near lapsing', () => {
    expect(expiringWithin([bond], d('2025-03-25'), 30)).toEqual([]);
  });
});

describe('describeBond', () => {
  it('says the bond covers the retention', () => {
    expect(describeBond(bondPosition(bond, gbp(5000), d('2025-03-25')))).toBe(
      'RB-01 with Northgate Surety, covers the retention in full',
    );
  });

  it('says how far short it falls', () => {
    expect(describeBond(bondPosition(bond, gbp(12_000), d('2025-03-25')))).toContain(
      '2635 short of the retention',
    );
  });

  it('says so when it has lapsed', () => {
    expect(describeBond(bondPosition(bond, gbp(5000), d('2026-12-01')))).toBe(
      'RB-01 expired on 2026-11-01',
    );
  });
});
