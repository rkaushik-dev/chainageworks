import { describe, expect, it } from 'vitest';
import { BadInputError, ConflictError, NotFoundError } from '../../../src/errors.js';
import { moneyFromMajor, zeroMoney } from '../../../src/domain/money/money.js';
import { percent } from '../../../src/domain/money/percent.js';
import { quantity } from '../../../src/domain/quantity/quantity.js';
import { buildCalendar } from '../../../src/domain/time/calendar.js';
import { importSurveyRecords } from '../../../src/ingest/records.js';
import { buildProgressReport } from '../../../src/reporting/progressReport.js';
import { buildStripMap } from '../../../src/reporting/stripMap.js';
import { buildSCurve, linearBaseline } from '../../../src/reporting/sCurve.js';
import { requirePackage } from '../../../src/domain/packages/register.js';
import { buildCertificate } from '../../../src/valuation/certificate.js';
import { buildSchedule, periodByNumber } from '../../../src/valuation/period.js';
import { DEFAULT_RETENTION } from '../../../src/valuation/retention.js';
import {
  certificateDto,
  curveDto,
  importReportDto,
  moneyDto,
  percentDto,
  periodDto,
  progressReportDto,
  quantityDto,
  stripDto,
} from '../../../src/http/dto.js';
import {
  describeFailure,
  isClientFailure,
  statusFor,
  toFailure,
} from '../../../src/http/problem.js';
import { d, sampleContext } from '../../support/contextFixtures.js';

const context = sampleContext();
const schedule = buildSchedule({
  firstYear: 2025,
  firstMonth: 1,
  count: 6,
  cutOffDay: 25,
  calendar: buildCalendar({ id: 'C1', name: 'main works' }),
});
const march = periodByNumber(schedule, 3);

describe('the value shapes', () => {
  it('sends money three ways so no client has to parse a symbol', () => {
    expect(moneyDto(moneyFromMajor(1234.56, 'GBP'))).toEqual({
      minor: 123_456,
      amount: 1234.56,
      formatted: '£1,234.56',
    });
  });

  it('sends a quantity with its unit', () => {
    expect(quantityDto(quantity(12.5, 'm3'))).toEqual({
      milli: 12_500,
      amount: 12.5,
      unit: 'm3',
      formatted: '12.50 m3',
    });
  });

  it('sends a percentage as a plain number', () => {
    expect(percentDto(percent(32.5))).toBe(32.5);
  });

  it('sends every date on a period', () => {
    expect(periodDto(march)).toEqual({
      number: 3,
      from: '2025-02-26',
      to: '2025-03-25',
      cutOff: '2025-03-25',
      issuedOn: '2025-04-03',
      dueOn: '2025-04-17',
    });
  });
});

describe('progressReportDto', () => {
  const dto = progressReportDto(buildProgressReport(context, march));

  it('survives a trip through JSON', () => {
    expect(() => JSON.stringify(dto)).not.toThrow();
    expect(JSON.parse(JSON.stringify(dto))).toEqual(dto);
  });

  it('carries the headline figures and the packages', () => {
    expect(dto['billRevision']).toBe('Rev A');
    expect((dto['earned'] as { amount: number }).amount).toBe(16_152.5);
    expect(dto['packages']).toHaveLength(3);
  });

  it('carries the step detail with the held chainage', () => {
    const first = (dto['packages'] as Record<string, unknown>[])[0]!;
    const steps = first['steps'] as Record<string, unknown>[];
    expect(steps).toHaveLength(4);
    expect(steps[1]?.['builtPercent']).toBe(75);
    expect(steps[1]?.['heldBack']).toEqual(['12+400.000 to 12+600.000']);
  });

  it('carries the reconciliation and the over measures', () => {
    expect(dto['reconciliation']).toHaveLength(3);
    expect(dto['overMeasures']).toEqual([]);
  });
});

describe('certificateDto', () => {
  const dto = certificateDto(
    buildCertificate({
      context,
      period: march,
      contractSum: moneyFromMajor(312_190, 'GBP'),
      retention: DEFAULT_RETENTION,
      previouslyCertified: zeroMoney('GBP'),
    }),
  );

  it('walks the whole certificate down to the amount due', () => {
    expect((dto['gross'] as { amount: number }).amount).toBe(16_152.5);
    expect((dto['amountDue'] as { amount: number }).amount).toBe(15_344.87);
    expect((dto['retention'] as Record<string, unknown>)['capReached']).toBe(false);
  });

  it('carries the backing sheet a package at a time', () => {
    const packages = dto['packages'] as Record<string, unknown>[];
    expect(packages).toHaveLength(3);
    expect(packages[0]?.['percent']).toBe(32.5);
    expect(packages[0]?.['allocations'] as unknown[]).toHaveLength(2);
  });

  it('survives a trip through JSON', () => {
    expect(JSON.parse(JSON.stringify(dto))).toEqual(dto);
  });
});

describe('curveDto and stripDto', () => {
  it('sends one row per curve point', () => {
    const points = buildSCurve(
      context,
      schedule,
      linearBaseline(moneyFromMajor(120_000, 'GBP'), 6),
    );
    const dto = curveDto(points);
    expect(dto).toHaveLength(6);
    expect(dto[2]?.['period']).toBe(3);
    expect(dto[2]?.['cutOff']).toBe('2025-03-25');
  });

  it('sends the strip as glyphs and as cells', () => {
    const map = buildStripMap(
      context,
      requirePackage(context.register, 'WP-01'),
      d('2025-03-31'),
      100_000,
    );
    const dto = stripDto(map);
    const rows = dto['rows'] as Record<string, unknown>[];
    expect(rows[1]?.['glyphs']).toBe('####oo..');
    expect(rows[1]?.['cells'] as unknown[]).toHaveLength(8);
  });
});

describe('importReportDto', () => {
  it('counts the rows and lists the issues', () => {
    const report = importSurveyRecords(
      [
        'record,package,code,step,from,to,quantity,unit,measured on,booked by',
        'SR-01,WP-01,E423.1,excavate,12+000,12+200,1800,m3,2025-03-06,J Foreman',
        'SR-02,WP-01,nonsense,excavate,12+200,12+400,1750,m3,2025-03-13,J Foreman',
      ].join('\n'),
    );
    expect(importReportDto(report)).toEqual({
      rowsRead: 2,
      accepted: 1,
      rejected: 1,
      issues: [
        {
          line: 3,
          column: 'code',
          severity: 'error',
          message: 'not a bill item code',
        },
      ],
    });
  });
});

describe('toFailure', () => {
  it('maps every code to a status', () => {
    expect(statusFor('bad_input')).toBe(400);
    expect(statusFor('not_found')).toBe(404);
    expect(statusFor('conflict')).toBe(409);
    expect(statusFor('unsupported')).toBe(501);
    expect(statusFor('internal')).toBe(500);
  });

  it('turns a domain error into a status and a body', () => {
    const failure = toFailure(new NotFoundError('work package', 'WP-99'));
    expect(failure.status).toBe(404);
    expect(failure.body).toEqual({
      code: 'not_found',
      message: 'no work package with id WP-99',
      details: { kind: 'work package', id: 'WP-99' },
    });
    expect(isClientFailure(failure)).toBe(true);
  });

  it('turns anything else into a 500', () => {
    const failure = toFailure(new TypeError('not a number'));
    expect(failure.status).toBe(500);
    expect(failure.body.code).toBe('internal');
    expect(isClientFailure(failure)).toBe(false);
  });

  it('describes itself for the request log', () => {
    expect(describeFailure(toFailure(new BadInputError('nope')))).toBe('400 bad_input: nope');
    expect(describeFailure(toFailure(new ConflictError('locked')))).toBe(
      '409 conflict: locked',
    );
  });
});
