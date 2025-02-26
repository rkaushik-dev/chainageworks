import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import { buildCalendar } from '../../../../src/domain/time/calendar.js';
import { formatSiteDate, parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import { buildDelayEvent } from '../../../../src/domain/delay/event.js';
import {
  buildCompletionTerms,
  completionPosition,
  daysLate,
  describeCompletion,
  isDamagesCapped,
  isLate,
  liquidatedDamages,
} from '../../../../src/domain/delay/completion.js';

const d = parseSiteDate;
const calendar = buildCalendar({ id: 'C1', name: 'main works' });
const contractSum = moneyFromMajor(2_000_000, 'GBP');

const terms = buildCompletionTerms({
  baseCompletion: d('2025-09-26'),
  damagesPerDay: moneyFromMajor(2500, 'GBP'),
  damagesCap: percent(10),
});

const notified = buildDelayEvent({
  id: 'DE-01',
  reference: 'EW/2025/003',
  cause: 'utilities',
  description: 'live water main outside its recorded position',
  from: d('2025-03-03'),
  to: d('2025-03-14'),
  notifiedOn: d('2025-03-05'),
  awardedDays: 8,
});

const barred = buildDelayEvent({
  id: 'DE-02',
  reference: 'EW/2025/004',
  cause: 'design',
  description: 'revised drainage layout issued late',
  from: d('2025-04-07'),
  to: d('2025-04-18'),
  notifiedOn: null,
  awardedDays: 5,
});

describe('buildCompletionTerms', () => {
  it('defaults the notice period to the contract standard', () => {
    expect(terms.noticeDays).toBe(21);
    expect(terms.damagesCap).toEqual(percent(10));
  });

  it('refuses negative damages and a nonsense notice period', () => {
    expect(() =>
      buildCompletionTerms({
        baseCompletion: d('2025-09-26'),
        damagesPerDay: moneyFromMajor(-1, 'GBP'),
      }),
    ).toThrow(/cannot be negative/);
    expect(() =>
      buildCompletionTerms({
        baseCompletion: d('2025-09-26'),
        damagesPerDay: moneyFromMajor(1, 'GBP'),
        noticeDays: -1,
      }),
    ).toThrow(/whole number of days/);
  });
});

describe('completionPosition', () => {
  it('leaves completion where it was when nothing has happened', () => {
    const position = completionPosition(terms, [], calendar, d('2025-03-31'));
    expect(formatSiteDate(position.extendedCompletion)).toBe('2025-09-26');
    expect(position.awardedDays).toBe(0);
    expect(position.barredDays).toBe(0);
  });

  it('moves completion by the working days awarded', () => {
    const position = completionPosition(terms, [notified], calendar, d('2025-03-31'));
    expect(position.awardedDays).toBe(8);
    expect(formatSiteDate(position.extendedCompletion)).toBe('2025-10-08');
  });

  it('steps over weekends, because an extension is in working days', () => {
    const position = completionPosition(terms, [notified], calendar, d('2025-03-31'));
    expect(formatSiteDate(position.extendedCompletion)).not.toBe('2025-10-04');
  });

  it('reports the days a bar shut out rather than dropping them', () => {
    const position = completionPosition(terms, [notified, barred], calendar, d('2025-05-31'));
    expect(position.awardedDays).toBe(8);
    expect(position.barredDays).toBe(5);
    expect(position.barred).toHaveLength(1);
    expect(position.barred[0]?.reason).toBe('not_notified');
    expect(position.barred[0]?.event.id).toBe('DE-02');
  });

  it('leaves out an event that has not happened yet', () => {
    const position = completionPosition(terms, [notified, barred], calendar, d('2025-03-31'));
    expect(position.barredDays).toBe(0);
  });

  it('takes the working days lost when a barred event was never assessed', () => {
    const unassessed = buildDelayEvent({
      id: 'DE-03',
      reference: 'EW/2025/005',
      cause: 'access',
      description: 'no access to the north end',
      from: d('2025-04-07'),
      to: d('2025-04-11'),
      notifiedOn: null,
    });
    const position = completionPosition(terms, [unassessed], calendar, d('2025-05-31'));
    expect(position.barredDays).toBe(5);
  });

  it('honours a longer notice period agreed in the contract', () => {
    const late = buildDelayEvent({
      id: 'DE-04',
      reference: 'EW/2025/006',
      cause: 'ground',
      description: 'rock at formation',
      from: d('2025-03-03'),
      to: d('2025-03-14'),
      notifiedOn: d('2025-03-28'),
      awardedDays: 6,
    });
    const generous = buildCompletionTerms({
      baseCompletion: d('2025-09-26'),
      damagesPerDay: moneyFromMajor(2500, 'GBP'),
      noticeDays: 40,
    });
    expect(completionPosition(terms, [late], calendar, d('2025-03-31')).awardedDays).toBe(0);
    expect(completionPosition(generous, [late], calendar, d('2025-03-31')).awardedDays).toBe(6);
  });
});

describe('daysLate and liquidatedDamages', () => {
  const position = completionPosition(terms, [notified], calendar, d('2025-12-31'));

  it('is not late before the extended date', () => {
    expect(daysLate(position, d('2025-10-08'))).toBe(0);
    expect(isLate(position, d('2025-10-08'))).toBe(false);
    expect(liquidatedDamages(terms, position, contractSum, d('2025-10-08')).minor).toBe(0);
  });

  it('counts calendar days past the extended date, not working days', () => {
    expect(daysLate(position, d('2025-10-15'))).toBe(7);
    expect(moneyMajor(liquidatedDamages(terms, position, contractSum, d('2025-10-15')))).toBe(
      17_500,
    );
  });

  it('would have been later without the extension', () => {
    const noEvents = completionPosition(terms, [], calendar, d('2025-12-31'));
    expect(daysLate(noEvents, d('2025-10-15'))).toBeGreaterThan(
      daysLate(position, d('2025-10-15')),
    );
  });

  it('stops at the contract cap', () => {
    const veryLate = d('2026-06-30');
    expect(moneyMajor(liquidatedDamages(terms, position, contractSum, veryLate))).toBe(200_000);
    expect(isDamagesCapped(terms, position, contractSum, veryLate)).toBe(true);
    expect(isDamagesCapped(terms, position, contractSum, d('2025-10-15'))).toBe(false);
  });

  it('runs on without a cap', () => {
    const uncapped = buildCompletionTerms({
      baseCompletion: d('2025-09-26'),
      damagesPerDay: moneyFromMajor(2500, 'GBP'),
    });
    const uncappedPosition = completionPosition(
      uncapped,
      [notified],
      calendar,
      d('2025-12-31'),
    );
    expect(
      moneyMajor(liquidatedDamages(uncapped, uncappedPosition, contractSum, d('2026-06-30'))),
    ).toBeGreaterThan(200_000);
    expect(isDamagesCapped(uncapped, uncappedPosition, contractSum, d('2026-06-30'))).toBe(
      false,
    );
  });
});

describe('describeCompletion', () => {
  it('says so when nothing has moved', () => {
    const position = completionPosition(terms, [], calendar, d('2025-03-31'));
    expect(describeCompletion(position)).toBe(
      'completion 2025-09-26 not moved, 0 days awarded, 0 days barred',
    );
  });

  it('names the new date and both counts', () => {
    const position = completionPosition(terms, [notified, barred], calendar, d('2025-05-31'));
    expect(describeCompletion(position)).toBe(
      'completion 2025-09-26 moved to 2025-10-08, 8 days awarded, 5 days barred',
    );
  });
});
