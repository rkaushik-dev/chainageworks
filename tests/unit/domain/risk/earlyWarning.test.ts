import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import {
  buildEarlyWarning,
  describeWarning,
  expectedCost,
  isOpenAt,
  meetingOverdue,
  riskPosition,
  unowned,
} from '../../../../src/domain/risk/earlyWarning.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import { range } from '../../../support/contextFixtures.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');

const utilities = buildEarlyWarning({
  id: 'EW-01',
  reference: 'EW/2025/001',
  description: 'unrecorded services under the roundabout',
  owner: 'employer',
  extent: range('12+400', '12+600'),
  raisedOn: d('2025-02-10'),
  meetingOn: d('2025-02-18'),
  costIfItHappens: gbp(60_000),
  likelihood: percent(40),
  delayDays: 12,
});

const ground = buildEarlyWarning({
  id: 'EW-02',
  reference: 'EW/2025/002',
  description: 'soft spots in the formation south of the bridge',
  owner: 'contractor',
  raisedOn: d('2025-02-20'),
  costIfItHappens: gbp(20_000),
  likelihood: percent(75),
  delayDays: 5,
});

const disputed = buildEarlyWarning({
  id: 'EW-03',
  reference: 'EW/2025/003',
  description: 'access through the farm gate in wet weather',
  owner: 'unresolved',
  raisedOn: d('2025-01-05'),
  costIfItHappens: gbp(10_000),
  likelihood: percent(50),
});

const shared = buildEarlyWarning({
  id: 'EW-04',
  reference: 'EW/2025/004',
  description: 'temporary works design still to be signed',
  owner: 'shared',
  raisedOn: d('2025-03-01'),
  meetingOn: d('2025-03-05'),
  costIfItHappens: gbp(8000),
  likelihood: percent(50),
});

const closed = buildEarlyWarning({
  id: 'EW-05',
  reference: 'EW/2025/005',
  description: 'a crane oversail that was agreed',
  owner: 'contractor',
  state: 'avoided',
  raisedOn: d('2025-01-10'),
  closedOn: d('2025-02-01'),
  costIfItHappens: gbp(30_000),
});

const all = [utilities, ground, disputed, shared, closed];

describe('buildEarlyWarning', () => {
  it('fills in the reference, the state and the likelihood', () => {
    expect(disputed.reference).toBe('EW/2025/003');
    expect(disputed.state).toBe('open');
    expect(disputed.delayDays).toBe(0);
    expect(disputed.extent).toBeNull();
  });

  it('refuses a warning that says nothing', () => {
    expect(() =>
      buildEarlyWarning({
        id: 'EW-09',
        description: '  ',
        owner: 'contractor',
        raisedOn: d('2025-01-01'),
        costIfItHappens: gbp(100),
      }),
    ).toThrow(/what the risk is/);
  });

  it('refuses a negative cost', () => {
    expect(() =>
      buildEarlyWarning({
        id: 'EW-09',
        description: 'a risk',
        owner: 'contractor',
        raisedOn: d('2025-01-01'),
        costIfItHappens: gbp(-1),
      }),
    ).toThrow(/less than nothing/);
  });

  it('refuses a likelihood outside nought to a hundred', () => {
    expect(() =>
      buildEarlyWarning({
        id: 'EW-09',
        description: 'a risk',
        owner: 'contractor',
        raisedOn: d('2025-01-01'),
        costIfItHappens: gbp(100),
        likelihood: percent(140),
      }),
    ).toThrow(/runs from 0 to 100/);
  });

  it('refuses half a working day of delay', () => {
    expect(() =>
      buildEarlyWarning({
        id: 'EW-09',
        description: 'a risk',
        owner: 'contractor',
        raisedOn: d('2025-01-01'),
        costIfItHappens: gbp(100),
        delayDays: 1.5,
      }),
    ).toThrow(/whole number of working days/);
  });

  it('refuses a warning closed before it was raised', () => {
    expect(() =>
      buildEarlyWarning({
        id: 'EW-09',
        description: 'a risk',
        owner: 'contractor',
        raisedOn: d('2025-02-01'),
        closedOn: d('2025-01-01'),
        costIfItHappens: gbp(100),
      }),
    ).toThrow(/close before it was raised/);
  });
});

describe('isOpenAt', () => {
  it('is not open before it was raised', () => {
    expect(isOpenAt(ground, d('2025-02-19'))).toBe(false);
    expect(isOpenAt(ground, d('2025-02-20'))).toBe(true);
  });

  it('stops being open on the day it closes', () => {
    expect(isOpenAt(closed, d('2025-01-31'))).toBe(true);
    expect(isOpenAt(closed, d('2025-02-01'))).toBe(false);
  });
});

describe('expectedCost', () => {
  it('weights the cost by the likelihood', () => {
    expect(moneyMajor(expectedCost(utilities))).toBe(24_000);
    expect(moneyMajor(expectedCost(ground))).toBe(15_000);
  });

  it('is the whole cost once the risk has happened', () => {
    const happened = buildEarlyWarning({ ...ground, id: 'EW-08', state: 'happened' });
    expect(moneyMajor(expectedCost(happened))).toBe(20_000);
  });

  it('is nothing once the risk has been avoided', () => {
    expect(expectedCost(closed).minor).toBe(0);
  });
});

describe('meetingOverdue', () => {
  it('is overdue when nobody has held a meeting inside the window', () => {
    expect(meetingOverdue(disputed, d('2025-03-25'))).toBe(true);
  });

  it('is not overdue while the window is still open', () => {
    expect(meetingOverdue(disputed, d('2025-01-15'))).toBe(false);
  });

  it('is overdue when the meeting was held late', () => {
    const late = buildEarlyWarning({ ...ground, id: 'EW-07', meetingOn: d('2025-03-20') });
    expect(meetingOverdue(late, d('2025-03-25'))).toBe(true);
  });

  it('is not overdue when the meeting was held in time', () => {
    expect(meetingOverdue(utilities, d('2025-03-25'))).toBe(false);
  });
});

describe('riskPosition', () => {
  const position = riskPosition(all, d('2025-03-25'), 'GBP');

  it('takes the open warnings, dearest expected cost first', () => {
    expect(position.open.map((w) => w.id)).toEqual(['EW-01', 'EW-02', 'EW-03', 'EW-04']);
  });

  it('keeps the expected cost apart from the worst case', () => {
    expect(moneyMajor(position.expected)).toBe(48_000);
    expect(moneyMajor(position.worstCase)).toBe(98_000);
  });

  it('counts a shared risk half to the contractor', () => {
    expect(moneyMajor(position.carried)).toBe(17_000);
  });

  it('names the meetings nobody held', () => {
    expect(position.overdueMeetings.map((w) => w.id)).toEqual(['EW-02', 'EW-03']);
  });

  it('adds the delay days across the open risks', () => {
    expect(position.delayDays).toBe(17);
  });

  it('is empty on a register with nothing on it', () => {
    const clean = riskPosition([], d('2025-03-25'), 'GBP');
    expect(clean.open).toEqual([]);
    expect(clean.expected.minor).toBe(0);
    expect(clean.delayDays).toBe(0);
  });
});

describe('unowned', () => {
  it('names the risks nobody has agreed an owner for', () => {
    expect(unowned(riskPosition(all, d('2025-03-25'), 'GBP')).map((w) => w.id)).toEqual([
      'EW-03',
    ]);
  });
});

describe('describeWarning', () => {
  it('says the owner, the state and whether a meeting was held', () => {
    expect(describeWarning(utilities)).toBe('EW/2025/001 employer open, met 2025-02-18');
    expect(describeWarning(ground)).toBe('EW/2025/002 contractor open, no meeting held');
  });
});
