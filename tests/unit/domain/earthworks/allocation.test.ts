import { describe, expect, it } from 'vitest';
import type { HaulSite } from '../../../../src/domain/earthworks/allocation.js';
import {
  allocateHaul,
  describeMovement,
  emptyAllocation,
  haulMoment,
  isBalanced,
  movementsFrom,
  movementsTo,
} from '../../../../src/domain/earthworks/allocation.js';
import { moneyFromMajor, moneyMajor, zeroMoney } from '../../../../src/domain/money/money.js';
import { quantity, quantityAmount } from '../../../../src/domain/quantity/quantity.js';

const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const m3 = (amount: number) => quantity(amount, 'm3');
const free = zeroMoney('GBP');

const cut: HaulSite = {
  id: 'CUT-A',
  name: 'cutting at 12+400',
  kind: 'cut',
  volume: m3(6000),
  unitCost: free,
};

const borrow: HaulSite = {
  id: 'BOR-1',
  name: 'borrow pit off the haul road',
  kind: 'borrow',
  volume: m3(4000),
  unitCost: gbp(3),
};

const embankment: HaulSite = {
  id: 'EMB-B',
  name: 'embankment at 13+200',
  kind: 'fill',
  volume: m3(7000),
  unitCost: free,
};

const tip: HaulSite = {
  id: 'TIP-1',
  name: 'licensed tip',
  kind: 'tip',
  volume: m3(5000),
  unitCost: gbp(9),
};

const haulRate = gbp(1);

describe('allocateHaul', () => {
  const allocation = allocateHaul({
    sources: [cut, borrow],
    destinations: [embankment, tip],
    links: [
      { fromId: 'CUT-A', toId: 'EMB-B', km: 3 },
      { fromId: 'CUT-A', toId: 'TIP-1', km: 1 },
      { fromId: 'BOR-1', toId: 'EMB-B', km: 2 },
      { fromId: 'BOR-1', toId: 'TIP-1', km: 4 },
    ],
    haulRate,
  });

  it('prefers a long haul from the cutting over a short one to the tip', () => {
    const first = allocation.movements[0]!;
    expect(first.fromId).toBe('CUT-A');
    expect(first.toId).toBe('EMB-B');
    expect(quantityAmount(first.volume)).toBe(6000);
  });

  it('buys from the borrow pit only for what the cutting could not fill', () => {
    const fromBorrow = movementsFrom(allocation, 'BOR-1');
    expect(fromBorrow).toHaveLength(1);
    expect(fromBorrow[0]!.toId).toBe('EMB-B');
    expect(quantityAmount(fromBorrow[0]!.volume)).toBe(1000);
  });

  it('never sends material to a tip it does not have to', () => {
    expect(movementsTo(allocation, 'TIP-1')).toEqual([]);
  });

  it('prices the haul and the site charges apart', () => {
    const first = allocation.movements[0]!;
    expect(moneyMajor(first.haulCost)).toBe(18_000);
    expect(first.siteCost.minor).toBe(0);
    expect(moneyMajor(first.cost)).toBe(18_000);
  });

  it('adds the borrow pit charge to its own movement', () => {
    const fromBorrow = movementsFrom(allocation, 'BOR-1')[0]!;
    expect(moneyMajor(fromBorrow.haulCost)).toBe(2000);
    expect(moneyMajor(fromBorrow.siteCost)).toBe(3000);
    expect(moneyMajor(fromBorrow.cost)).toBe(5000);
  });

  it('adds the movements up', () => {
    expect(moneyMajor(allocation.cost)).toBe(23_000);
  });

  it('never buys material in order to throw it away', () => {
    const wasteful = allocateHaul({
      sources: [borrow],
      destinations: [tip],
      links: [{ fromId: 'BOR-1', toId: 'TIP-1', km: 1 }],
      haulRate,
    });
    expect(wasteful.movements).toEqual([]);
    expect(quantityAmount(wasteful.unplaced)).toBe(4000);
  });

  it('reports what is left over at each end', () => {
    expect(quantityAmount(allocation.unplaced)).toBe(3000);
    expect(quantityAmount(allocation.unfilled)).toBe(0);
    expect(isBalanced(allocation)).toBe(false);
  });

  it('counts the room left at the tip as spare rather than a shortfall', () => {
    expect(quantityAmount(allocation.spareTipCapacity)).toBe(5000);
  });
});

describe('a balanced job', () => {
  const balanced = allocateHaul({
    sources: [{ ...cut, volume: m3(5000) }],
    destinations: [{ ...embankment, volume: m3(5000) }],
    links: [{ fromId: 'CUT-A', toId: 'EMB-B', km: 2 }],
    haulRate,
  });

  it('places everything and fills everything', () => {
    expect(isBalanced(balanced)).toBe(true);
    expect(balanced.unplaced.milli).toBe(0);
    expect(balanced.unfilled.milli).toBe(0);
  });

  it('gives the haul moment plant is sized on', () => {
    expect(haulMoment(balanced)).toBe(10_000);
  });
});

describe('a link nobody can use', () => {
  it('leaves out a link naming a site that is not on the job', () => {
    const allocation = allocateHaul({
      sources: [cut],
      destinations: [embankment],
      links: [
        { fromId: 'CUT-A', toId: 'EMB-B', km: 2 },
        { fromId: 'CUT-Z', toId: 'EMB-B', km: 1 },
      ],
      haulRate,
    });
    expect(allocation.movements).toHaveLength(1);
  });

  it('moves nothing when nothing is linked at all', () => {
    const allocation = allocateHaul({
      sources: [cut],
      destinations: [embankment],
      links: [],
      haulRate,
    });
    expect(allocation.movements).toEqual([]);
    expect(quantityAmount(allocation.unplaced)).toBe(6000);
    expect(quantityAmount(allocation.unfilled)).toBe(7000);
    expect(allocation.spareTipCapacity.milli).toBe(0);
  });
});

describe('refusing bad sites', () => {
  it('refuses a site measured in anything but cubic metres', () => {
    expect(() =>
      allocateHaul({
        sources: [{ ...cut, volume: quantity(6000, 't') }],
        destinations: [embankment],
        links: [],
        haulRate,
      }),
    ).toThrow(/cubic metres/);
  });

  it('refuses a negative volume', () => {
    expect(() =>
      allocateHaul({
        sources: [cut],
        destinations: [{ ...embankment, volume: m3(-1) }],
        links: [],
        haulRate,
      }),
    ).toThrow(/negative volume/);
  });
});

describe('emptyAllocation', () => {
  it('is a plan that moves nothing and costs nothing', () => {
    const empty = emptyAllocation('GBP');
    expect(empty.movements).toEqual([]);
    expect(empty.cost.minor).toBe(0);
    expect(isBalanced(empty)).toBe(true);
    expect(haulMoment(empty)).toBe(0);
  });
});

describe('describeMovement', () => {
  it('says what goes where and how far', () => {
    const allocation = allocateHaul({
      sources: [{ ...cut, volume: m3(5000) }],
      destinations: [{ ...embankment, volume: m3(5000) }],
      links: [{ fromId: 'CUT-A', toId: 'EMB-B', km: 2 }],
      haulRate,
    });
    expect(describeMovement(allocation.movements[0]!)).toBe(
      '5000 m3 from CUT-A to EMB-B, 2 km',
    );
  });
});
