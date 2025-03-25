import { describe, expect, it } from 'vitest';
import { buildCalendar } from '../../../../src/domain/time/calendar.js';
import { formatSiteDate, parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import type { DefectInput } from '../../../../src/domain/defects/defect.js';
import {
  buildDefect,
  canIssueDefectsCertificate,
  certificateBlock,
  describeDefect,
  dueDateFor,
  isOutstandingAt,
  isOverdueAt,
  outstanding,
  overdue,
  RECTIFICATION_DAYS,
  summarise,
} from '../../../../src/domain/defects/defect.js';

const d = parseSiteDate;
const calendar = buildCalendar({ id: 'C1', name: 'main works' });

const base: DefectInput = {
  id: 'DF-01',
  reference: 'DEF/2025/011',
  packageId: 'WP-03',
  description: 'surface course ravelling at the joint',
  raisedOn: d('2025-11-03'),
  dueBy: d('2025-12-01'),
};

const minor = buildDefect(base);

const safety = buildDefect({
  ...base,
  id: 'DF-02',
  reference: 'DEF/2025/012',
  description: 'missing safety barrier at the retaining wall',
  severity: 'safety',
  raisedOn: d('2025-11-05'),
  dueBy: d('2025-11-06'),
});

describe('buildDefect', () => {
  it('defaults to minor and to the contractor', () => {
    expect(minor.severity).toBe('minor');
    expect(minor.attribution).toBe('contractor');
    expect(minor.rectifiedOn).toBeNull();
    expect(minor.extent).toBeNull();
  });

  it('refuses an empty description', () => {
    expect(() => buildDefect({ ...base, description: ' ' })).toThrow(/needs a description/);
  });

  it('refuses a due date before the defect was raised', () => {
    expect(() => buildDefect({ ...base, dueBy: d('2025-10-01') })).toThrow(
      /due before it was raised/,
    );
  });

  it('refuses a rectification before the defect was raised', () => {
    expect(() => buildDefect({ ...base, rectifiedOn: d('2025-10-01') })).toThrow(
      /put right before it was raised/,
    );
  });
});

describe('dueDateFor', () => {
  it('gives a safety defect one working day', () => {
    expect(RECTIFICATION_DAYS.safety).toBe(1);
    expect(formatSiteDate(dueDateFor('safety', d('2025-11-03'), calendar))).toBe('2025-11-04');
  });

  it('gives a major defect two working weeks and a minor one four', () => {
    expect(formatSiteDate(dueDateFor('major', d('2025-11-03'), calendar))).toBe('2025-11-17');
    expect(formatSiteDate(dueDateFor('minor', d('2025-11-03'), calendar))).toBe('2025-12-01');
  });

  it('steps over a weekend rather than counting it', () => {
    expect(formatSiteDate(dueDateFor('safety', d('2025-11-07'), calendar))).toBe('2025-11-10');
  });
});

describe('isOutstandingAt', () => {
  it('is not outstanding before it was raised', () => {
    expect(isOutstandingAt(minor, d('2025-11-02'))).toBe(false);
  });

  it('is outstanding from the day it was raised', () => {
    expect(isOutstandingAt(minor, d('2025-11-03'))).toBe(true);
    expect(isOutstandingAt(minor, d('2026-01-01'))).toBe(true);
  });

  it('stops being outstanding on the day it is put right', () => {
    const done = buildDefect({ ...base, rectifiedOn: d('2025-11-20') });
    expect(isOutstandingAt(done, d('2025-11-19'))).toBe(true);
    expect(isOutstandingAt(done, d('2025-11-20'))).toBe(false);
  });
});

describe('isOverdueAt', () => {
  it('is not overdue on the day it is due', () => {
    expect(isOverdueAt(minor, d('2025-12-01'))).toBe(false);
    expect(isOverdueAt(minor, d('2025-12-02'))).toBe(true);
  });

  it('is not overdue once it has been put right', () => {
    const done = buildDefect({ ...base, rectifiedOn: d('2025-11-20') });
    expect(isOverdueAt(done, d('2026-01-01'))).toBe(false);
  });
});

describe('the outstanding list', () => {
  const employerItem = buildDefect({
    ...base,
    id: 'DF-03',
    reference: 'DEF/2025/013',
    description: 'damage by a third party contractor',
    attribution: 'employer',
    raisedOn: d('2025-11-10'),
    dueBy: d('2025-11-24'),
  });
  const disputed = buildDefect({
    ...base,
    id: 'DF-04',
    reference: 'DEF/2025/014',
    description: 'cracking at the headwall, cause not agreed',
    severity: 'major',
    attribution: 'unresolved',
    raisedOn: d('2025-11-12'),
    dueBy: d('2025-11-26'),
  });
  const list = [minor, safety, employerItem, disputed];

  it('sorts by the date they fall due', () => {
    expect(outstanding(list, d('2025-11-15')).map((x) => x.id)).toEqual([
      'DF-02',
      'DF-03',
      'DF-04',
      'DF-01',
    ]);
  });

  it('reports what is overdue', () => {
    expect(overdue(list, d('2025-11-15')).map((x) => x.id)).toEqual(['DF-02']);
    expect(overdue(list, d('2025-11-06'))).toEqual([]);
  });

  it('summarises by severity and counts the disputes', () => {
    const summary = summarise(list, d('2025-11-15'));
    expect(summary.outstanding).toBe(4);
    expect(summary.overdue).toBe(1);
    expect(summary.bySeverity).toEqual({ minor: 2, major: 1, safety: 1 });
    expect(summary.disputed).toBe(1);
  });

  it('counts nothing before anything was raised', () => {
    expect(summarise(list, d('2025-01-01')).outstanding).toBe(0);
  });
});

describe('certificateBlock', () => {
  const ends = d('2026-11-01');

  it('refuses before the defects period has run', () => {
    expect(certificateBlock([], ends, d('2026-01-01'))).toBe('period_not_ended');
  });

  it('issues once the period has run and nothing is open', () => {
    expect(certificateBlock([], ends, d('2026-11-01'))).toBeNull();
    expect(canIssueDefectsCertificate([], ends, d('2026-11-01'))).toBe(true);
  });

  it('is stopped by an open safety defect above everything else', () => {
    expect(certificateBlock([safety, minor], ends, d('2026-11-01'))).toBe('safety_defect_open');
  });

  it('is stopped by an open contractor defect', () => {
    expect(certificateBlock([minor], ends, d('2026-11-01'))).toBe('contractor_defects_open');
  });

  it('is stopped while nobody has decided who caused it', () => {
    const disputed = buildDefect({ ...base, attribution: 'unresolved' });
    expect(certificateBlock([disputed], ends, d('2026-11-01'))).toBe('attribution_unresolved');
  });

  it('is not stopped by a defect the employer caused', () => {
    const employerItem = buildDefect({ ...base, attribution: 'employer' });
    expect(certificateBlock([employerItem], ends, d('2026-11-01'))).toBeNull();
  });

  it('is not stopped once everything has been put right', () => {
    const done = buildDefect({ ...base, rectifiedOn: d('2025-11-20') });
    expect(canIssueDefectsCertificate([done], ends, d('2026-11-01'))).toBe(true);
  });
});

describe('describeDefect', () => {
  it('reads like a list line while it is open', () => {
    expect(describeDefect(minor)).toBe(
      'DEF/2025/011 minor, due 2025-12-01: surface course ravelling at the joint',
    );
  });

  it('says when it was put right', () => {
    const done = buildDefect({ ...base, rectifiedOn: d('2025-11-20') });
    expect(describeDefect(done)).toContain('put right 2025-11-20');
  });
});
