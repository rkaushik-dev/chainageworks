import { describe, expect, it } from 'vitest';
import * as cw from '../../src/index.js';

/**
 * The public surface, named here as well as in the entry point.
 *
 * Two lists rather than one on purpose. A test that reads the entry point and
 * checks itself proves nothing; a test that carries its own list turns any
 * accidental removal into a failure and any deliberate one into an edit
 * somebody has to make twice, which is the point.
 */
const EXPECTED = [
  // errors and results
  'asCwError',
  'BadInputError',
  'ConflictError',
  'CwError',
  'isCwError',
  'NotFoundError',
  'UnsupportedError',
  'collectResults',
  'err',
  'ok',
  'partitionResults',
  // units
  'formatMoney',
  'money',
  'moneyFromMajor',
  'moneyMajor',
  'parseMoney',
  'formatPercent',
  'percent',
  'percentValue',
  'extend',
  'formatRate',
  'parseRate',
  'rate',
  'formatQuantity',
  'parseQuantity',
  'quantity',
  'isUnitCode',
  'parseUnit',
  'unitName',
  // where and when
  'asStation',
  'formatStation',
  'parseStation',
  'chainageRange',
  'coveredLength',
  'formatChainageRange',
  'intersectRangeLists',
  'subtractRange',
  'unionRanges',
  'buildAlignment',
  'locateStation',
  'stationAtDistance',
  'addDays',
  'compareSiteDates',
  'formatSiteDate',
  'parseSiteDate',
  'siteDate',
  'buildCalendar',
  'addWorkingDays',
  'workingDaysIn',
  // the bill and the packages
  'buildBillItem',
  'billedValue',
  'formatItemCode',
  'itemCode',
  'parseItemCode',
  'buildBill',
  'billAsAt',
  'buildBillHistory',
  'rateAsAt',
  'buildWorkPackage',
  'buildCreditProfile',
  'STANDARD_PROFILES',
  // what was built
  'buildSurveyRecord',
  'effectiveRecords',
  'buildInspectionPlan',
  'buildSignOff',
  'buildNcr',
  'buildProgressContext',
  'earnAll',
  'earnPackage',
  'buildLookahead',
  'readyNow',
  'buildAllowances',
  'packageOutput',
  // what it is worth
  'buildSchedule',
  'periodByNumber',
  'buildCertificate',
  'certificateLines',
  'certificateNotes',
  'certifySeries',
  'DEFAULT_RETENTION',
  'computeRetention',
  'bondPosition',
  'buildRetentionBond',
  'buildCashFlow',
  'peakExposure',
  'buildOutturn',
  'outturnParts',
  // the application
  'createJob',
  'jobBlockers',
  'jobContext',
  'jobFromJson',
  'jobToJson',
  'configFromEnv',
  'DEFAULT_CONFIG',
  'certificateFor',
  'createService',
  'outturnFor',
  'paymentsFor',
  'progressFor',
  'risksFor',
  'variationsFor',
  'diagnose',
  'isHealthy',
  'buildServer',
  'buildProgram',
  'runCli',
] as const;

describe('the public entry point', () => {
  it('exports everything the list names', () => {
    const exported = new Set(Object.keys(cw));
    const missing = EXPECTED.filter((name) => !exported.has(name));
    expect(missing).toEqual([]);
  });

  it('exports nothing the list does not name', () => {
    const named = new Set<string>(EXPECTED);
    const extra = Object.keys(cw).filter((name) => !named.has(name));
    expect(extra).toEqual([]);
  });

  it('exports a function or a value for every name', () => {
    const surface = cw as unknown as Record<string, unknown>;
    for (const name of EXPECTED) {
      expect([name, typeof surface[name]]).not.toEqual([name, 'undefined']);
    }
  });
});

describe('the entry point is enough to value a job', () => {
  it('builds a job, a bill and a certificate without reaching past it', () => {
    const job = cw.createJob({
      id: 'A1',
      name: 'a short job',
      contractSum: cw.moneyFromMajor(100_000, 'GBP'),
    });

    job.alignments.add(
      cw.buildAlignment({
        id: 'A1',
        name: 'main carriageway',
        start: cw.parseStation('0+000'),
        end: cw.parseStation('1+000'),
      }),
    );
    job.billRevisions.add({
      reference: 'Rev A',
      effectiveFrom: cw.parseSiteDate('2025-01-01'),
      note: 'award',
      bill: cw.buildBill({
        id: 'Rev A',
        title: 'bill of quantities',
        currency: 'GBP',
        items: [
          cw.buildBillItem({
            code: cw.parseItemCode('E423.1'),
            description: 'excavation',
            rate: cw.rate(5, 'GBP', 'm3'),
            quantity: cw.quantity(2000, 'm3'),
          }),
        ],
      }),
    });
    job.packages.add(
      cw.buildWorkPackage({
        id: 'WP-01',
        name: 'earthworks',
        alignmentId: 'A1',
        extent: cw.chainageRange(cw.parseStation('0+000'), cw.parseStation('1+000')),
        section: 'section 1',
        profileId: 'single-step',
        allocations: [{ code: cw.parseItemCode('E423.1'), quantity: cw.quantity(2000, 'm3') }],
      }),
    );

    const service = cw.createService(job, cw.DEFAULT_CONFIG);
    const certificate = cw.certificateFor(service, 1);

    expect(certificate.period.number).toBe(1);
    expect(cw.moneyMajor(certificate.gross)).toBe(0);
    expect(cw.certificateLines(certificate).length).toBeGreaterThan(5);
    expect(cw.jobBlockers(job)).toEqual([]);
  });

  it('round trips that job through the codec off the entry point', () => {
    const job = cw.createJob({
      id: 'A1',
      name: 'a short job',
      contractSum: cw.moneyFromMajor(100_000, 'GBP'),
    });
    const again = cw.jobFromJson(JSON.parse(JSON.stringify(cw.jobToJson(job))) as unknown);
    expect(again.settings.id).toBe('A1');
    expect(cw.moneyMajor(again.settings.contractSum)).toBe(100_000);
  });

  it('reports what an empty job is missing', () => {
    const job = cw.createJob({
      id: 'A1',
      name: 'a short job',
      contractSum: cw.moneyFromMajor(100_000, 'GBP'),
    });
    expect(cw.jobBlockers(job)).toHaveLength(3);
    expect(cw.isHealthy(cw.diagnose(job))).toBe(false);
  });
});
