import { describe, expect, it } from 'vitest';
import { buildCalendar } from '../../../../src/domain/time/calendar.js';
import { parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import type { DelayEventInput } from '../../../../src/domain/delay/event.js';
import {
  assessedDays,
  barReason,
  buildDelayEvent,
  carriesMoney,
  DEFAULT_NOTICE_DAYS,
  describeDelayEvent,
  elapsedDays,
  entitlementFor,
  eventsTo,
  isAssessed,
  isClaimable,
  workingDaysLost,
} from '../../../../src/domain/delay/event.js';

const d = parseSiteDate;
const calendar = buildCalendar({ id: 'C1', name: 'main works' });

const base: DelayEventInput = {
  id: 'DE-01',
  reference: 'EW/2025/003',
  cause: 'utilities',
  description: 'live water main found outside the recorded position',
  from: d('2025-03-03'),
  to: d('2025-03-14'),
  notifiedOn: d('2025-03-05'),
  packageIds: ['WP-01'],
};

const event = buildDelayEvent(base);

describe('entitlementFor', () => {
  it('gives weather time and no money', () => {
    expect(entitlementFor('weather')).toBe('time_only');
  });

  it('gives an employer cause both', () => {
    for (const cause of ['variation', 'access', 'design', 'utilities', 'ground'] as const) {
      expect(entitlementFor(cause)).toBe('time_and_money');
    }
  });

  it('gives a delay the contractor caused nothing', () => {
    expect(entitlementFor('contractor')).toBe('none');
  });
});

describe('buildDelayEvent', () => {
  it('keeps the period and the packages', () => {
    expect(elapsedDays(event)).toBe(12);
    expect(workingDaysLost(event, calendar)).toBe(10);
    expect(event.packageIds).toEqual(['WP-01']);
    expect(event.awardedDays).toBeNull();
    expect(isAssessed(event)).toBe(false);
  });

  it('refuses an empty description', () => {
    expect(() => buildDelayEvent({ ...base, description: '  ' })).toThrow(
      /needs a description/,
    );
  });

  it('refuses a period that ends before it starts', () => {
    expect(() => buildDelayEvent({ ...base, to: d('2025-03-01') })).toThrow(
      /ends on or after it starts/,
    );
  });

  it('refuses notice given before the event began', () => {
    expect(() => buildDelayEvent({ ...base, notifiedOn: d('2025-03-01') })).toThrow(
      /before the event started/,
    );
  });

  it('refuses an award that is not a whole number of days', () => {
    expect(() => buildDelayEvent({ ...base, awardedDays: -1 })).toThrow(/whole number/);
    expect(() => buildDelayEvent({ ...base, awardedDays: 2.5 })).toThrow(/whole number/);
  });
});

describe('the time bar', () => {
  it('lets a properly notified event through', () => {
    expect(barReason(event)).toBeNull();
    expect(isClaimable(event)).toBe(true);
    expect(carriesMoney(event)).toBe(true);
  });

  it('bars an event nobody gave notice of', () => {
    const silent = buildDelayEvent({ ...base, notifiedOn: null });
    expect(barReason(silent)).toBe('not_notified');
    expect(isClaimable(silent)).toBe(false);
  });

  it('bars notice given a day past the window', () => {
    const late = buildDelayEvent({ ...base, notifiedOn: d('2025-03-25') });
    expect(barReason(late)).toBe('notified_late');
    const justInTime = buildDelayEvent({ ...base, notifiedOn: d('2025-03-24') });
    expect(barReason(justInTime)).toBeNull();
    expect(DEFAULT_NOTICE_DAYS).toBe(21);
  });

  it('honours a different notice period', () => {
    const late = buildDelayEvent({ ...base, notifiedOn: d('2025-03-25') });
    expect(barReason(late, 30)).toBeNull();
    expect(barReason(event, 1)).toBe('notified_late');
  });

  it('bars a cause that carries no entitlement, however good the notice', () => {
    const ownFault = buildDelayEvent({ ...base, cause: 'contractor' });
    expect(barReason(ownFault)).toBe('no_entitlement');
  });

  it('gives weather time but no money', () => {
    const wet = buildDelayEvent({ ...base, cause: 'weather' });
    expect(isClaimable(wet)).toBe(true);
    expect(carriesMoney(wet)).toBe(false);
  });
});

describe('assessedDays', () => {
  it('takes the engineer award when there is one', () => {
    const awarded = buildDelayEvent({ ...base, awardedDays: 4 });
    expect(assessedDays(awarded, calendar)).toBe(4);
    expect(isAssessed(awarded)).toBe(true);
  });

  it('falls back to the working days the event ran for', () => {
    expect(assessedDays(event, calendar)).toBe(10);
  });

  it('is nothing for a barred event even when days were awarded', () => {
    const barred = buildDelayEvent({ ...base, notifiedOn: null, awardedDays: 4 });
    expect(assessedDays(barred, calendar)).toBe(0);
  });

  it('is nothing for an award of nought', () => {
    const refused = buildDelayEvent({ ...base, awardedDays: 0 });
    expect(assessedDays(refused, calendar)).toBe(0);
    expect(isAssessed(refused)).toBe(true);
  });

  it('counts fewer days on a calendar with a holiday in the window', () => {
    const easter = buildCalendar({
      id: 'C2',
      name: 'with holidays',
      holidays: [d('2025-03-05'), d('2025-03-06')],
    });
    expect(workingDaysLost(event, easter)).toBe(8);
  });
});

describe('eventsTo', () => {
  const later = buildDelayEvent({
    ...base,
    id: 'DE-02',
    from: d('2025-05-01'),
    to: d('2025-05-09'),
    notifiedOn: d('2025-05-02'),
  });

  it('takes the events that had started by the date, in order', () => {
    expect(eventsTo([later, event], d('2025-03-31')).map((e) => e.id)).toEqual(['DE-01']);
    expect(eventsTo([later, event], d('2025-05-31')).map((e) => e.id)).toEqual([
      'DE-01',
      'DE-02',
    ]);
  });

  it('takes an event that started on the day itself', () => {
    expect(eventsTo([event], d('2025-03-03'))).toHaveLength(1);
  });
});

describe('describeDelayEvent', () => {
  it('reads like a register line', () => {
    expect(describeDelayEvent(event)).toBe(
      'EW/2025/003 utilities, 2025-03-03 to 2025-03-14, claimable',
    );
  });

  it('says why it is barred', () => {
    const silent = buildDelayEvent({ ...base, notifiedOn: null });
    expect(describeDelayEvent(silent)).toContain('not notified');
  });
});
