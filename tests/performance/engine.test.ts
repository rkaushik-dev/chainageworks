import { describe, expect, it } from 'vitest';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import {
  certificateFor,
  createService,
  lookaheadFrom,
  outputFor,
  outturnFor,
  paymentsFor,
  progressFor,
} from '../../src/app/service.js';
import { earnAll } from '../../src/progress/earn.js';
import { jobContext } from '../../src/store/job.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import { importSurveyRecords } from '../../src/ingest/records.js';
import { d } from '../support/contextFixtures.js';
import { bigJob } from '../support/bigJob.js';

/**
 * Timing rather than correctness.
 *
 * The thresholds are deliberately loose, several times what the run takes on a
 * quiet machine, because a tight one on a shared runner is a test that fails
 * for reasons nobody can act on. What they catch is the change that makes
 * something quadratic, which shows up as an order of magnitude and not as a
 * few percent.
 */
function timed(work: () => void): number {
  const started = performance.now();
  work();
  return performance.now() - started;
}

const job = bigJob();
const context = jobContext(job);

describe('the size of the fixture', () => {
  it('is the size of a real contract', () => {
    expect(job.packages.count()).toBe(200);
    expect(job.records.count()).toBe(8000);
    expect(job.signOffs.count()).toBe(200);
  });

  it('carries a commercial side to match', () => {
    expect(job.subcontracts.count()).toBe(30);
    expect(job.paymentApplications.count()).toBe(360);
    expect(job.contraCharges.count()).toBe(90);
    expect(job.resourceReturns.count()).toBe(2000);
  });
});

describe('earning the whole job', () => {
  it('values two hundred packages in under four seconds', () => {
    const elapsed = timed(() => {
      earnAll(context, d('2025-06-25'));
    });
    expect(elapsed).toBeLessThan(4000);
  });

  it('costs about the same at a later cut off, not more', () => {
    const early = timed(() => {
      earnAll(context, d('2025-02-25'));
    });
    const late = timed(() => {
      earnAll(context, d('2026-02-25'));
    });
    expect(late).toBeLessThan(Math.max(early * 6, 4000));
  });
});

describe('the reports', () => {
  const service = createService(job, DEFAULT_CONFIG);

  it('builds a progress report in under six seconds', () => {
    const elapsed = timed(() => {
      progressFor(service, 6);
    });
    expect(elapsed).toBeLessThan(6000);
  });

  it('builds a certificate at period six in under twenty seconds', () => {
    const elapsed = timed(() => {
      certificateFor(service, 6);
    });
    expect(elapsed).toBeLessThan(20_000);
  });
});

describe('the codec', () => {
  it('writes and reads the whole job in under six seconds', () => {
    const elapsed = timed(() => {
      jobFromJson(JSON.parse(JSON.stringify(jobToJson(job))) as unknown);
    });
    expect(elapsed).toBeLessThan(6000);
  });
});

describe('the importer', () => {
  it('reads five thousand rows in under four seconds', () => {
    const header = 'record,package,code,step,from,to,quantity,unit,measured on,booked by';
    const rows = Array.from(
      { length: 5000 },
      (_, i) =>
        `SR-${String(i)},WP-0001,E423.1,excavate,12+000,12+100,120,m3,2025-03-06,J Foreman`,
    );
    const text = [header, ...rows].join('\n');
    let read = 0;
    const elapsed = timed(() => {
      read = importSurveyRecords(text).rowsRead;
    });
    expect(read).toBe(5000);
    expect(elapsed).toBeLessThan(4000);
  });
});

describe('the commercial reports', () => {
  const service = createService(job, DEFAULT_CONFIG);

  it('values thirty orders and their set off in under six seconds', () => {
    let orders = 0;
    const elapsed = timed(() => {
      orders = paymentsFor(service, 6).positions.length;
    });
    expect(orders).toBe(30);
    expect(elapsed).toBeLessThan(6000);
  });

  it('sets two thousand returns against the work in under fifteen seconds', () => {
    let packages = 0;
    const elapsed = timed(() => {
      packages = outputFor(service, 6).packages.length;
    });
    expect(packages).toBe(200);
    expect(elapsed).toBeLessThan(15_000);
  });

  it('rolls the whole account up in under twenty seconds', () => {
    const elapsed = timed(() => {
      outturnFor(service, 6);
    });
    expect(elapsed).toBeLessThan(20_000);
  });

  it('plans four weeks across two hundred packages in under ten seconds', () => {
    let steps = 0;
    const elapsed = timed(() => {
      steps = lookaheadFrom(service, d('2025-06-25')).steps.length;
    });
    expect(steps).toBeGreaterThan(0);
    expect(elapsed).toBeLessThan(10_000);
  });
});
