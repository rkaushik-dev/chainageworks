import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, revisionsFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { buildBill } from '../../src/domain/bill/bill.js';
import { buildBillItem } from '../../src/domain/bill/billItem.js';
import { parseItemCode } from '../../src/domain/bill/itemCode.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { rate } from '../../src/domain/money/rate.js';
import { quantity } from '../../src/domain/quantity/quantity.js';
import { buildServer } from '../../src/http/server.js';
import { rateChanges, renderRevisionReport } from '../../src/reporting/revisionReport.js';
import type { Job } from '../../src/store/job.js';
import { runCommand } from '../support/cliHarness.js';
import { sampleBill } from '../support/billFixtures.js';
import { d } from '../support/contextFixtures.js';
import { sampleJob } from '../support/jobFixtures.js';

/** The sample bill with the earthworks rate repriced and a star item added. */
function revisedJob(): Job {
  const job = sampleJob();
  const original = sampleBill();

  job.billRevisions.add({
    reference: 'Rev B',
    effectiveFrom: d('2025-03-01'),
    note: 'rock rate agreed after the trial pits',
    bill: buildBill({
      id: 'Rev B',
      title: original.title,
      currency: original.currency,
      items: [
        ...original.items.map((item) =>
          item.code.workClass === 'E' && item.code.divisions[0] === 4
            ? buildBillItem({
                code: item.code,
                description: item.description,
                rate: rate(6.4, 'GBP', 'm3'),
                quantity: item.quantity,
                kind: item.kind,
                chainage: item.chainage,
              })
            : item,
        ),
        buildBillItem({
          code: parseItemCode('E425.1'),
          description: 'excavation in rock, star rate',
          rate: rate(24.5, 'GBP', 'm3'),
          quantity: quantity(400, 'm3'),
          kind: 'star',
        }),
      ],
    }),
  });
  return job;
}

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(revisedJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(revisedJob(), DEFAULT_CONFIG);

describe('revisionsFor', () => {
  it('compares each revision with the one before it', () => {
    const report = revisionsFor(service());
    expect(report.steps).toHaveLength(1);
    expect(report.steps[0]!.from.reference).toBe('Rev A');
    expect(report.steps[0]!.to.reference).toBe('Rev B');
  });

  it('names the step that moved a rate', () => {
    const report = revisionsFor(service());
    expect(report.ratesMoved).toHaveLength(1);
  });

  it('prices the movement the revision made', () => {
    const report = revisionsFor(service());
    expect(moneyMajor(report.steps[0]!.diff.netDelta)).toBeGreaterThan(0);
  });

  it('sees the item the revision added', () => {
    const kinds = revisionsFor(service()).steps[0]!.diff.changes.map((change) => change.kind);
    expect(kinds).toContain('added');
    expect(kinds).toContain('rate_changed');
  });

  it('has no steps on a job with one revision', () => {
    const report = revisionsFor(createService(sampleJob(), DEFAULT_CONFIG));
    expect(report.steps).toEqual([]);
    expect(report.ratesMoved).toEqual([]);
  });
});

describe('rateChanges', () => {
  it('names only the rates that moved, with the revision that moved them', () => {
    const moves = rateChanges(revisionsFor(service()));
    expect(moves.length).toBeGreaterThan(0);
    expect(moves[0]).toContain('Rev B:');
    expect(moves[0]).toContain(' to ');
  });

  it('names nothing on a job with one revision', () => {
    expect(rateChanges(revisionsFor(createService(sampleJob(), DEFAULT_CONFIG)))).toEqual([]);
  });
});

describe('renderRevisionReport', () => {
  it('opens with the steps and then sets each one out', () => {
    const text = renderRevisionReport(revisionsFor(service()));
    expect(text.split('\n')[0]).toBe('bill revisions');
    expect(text).toMatch(/steps that moved a rate +1/);
    expect(text).toContain('Rev A to Rev B, effective 2025-03-01');
    expect(text).toContain('rate changed');
  });

  it('says so when there is nothing to compare', () => {
    const text = renderRevisionReport(revisionsFor(createService(sampleJob(), DEFAULT_CONFIG)));
    expect(text).toMatch(/steps +0/);
    expect(text).not.toContain('rate was');
  });
});

describe('GET /revisions', () => {
  it('answers with each step and the rate movements', async () => {
    const body = (await app.inject({ method: 'GET', url: '/revisions' })).json<{
      rateChanges: string[];
      steps: { from: string; to: string; changes: { kind: string }[] }[];
    }>();
    expect(body.steps).toHaveLength(1);
    expect(body.steps[0]!.from).toBe('Rev A');
    expect(body.rateChanges.length).toBeGreaterThan(0);
  });
});

describe('cw revisions', () => {
  it('prints the report', async () => {
    const run = await runCommand(['revisions'], { job: revisedJob() });
    expect(run.code).toBe(0);
    expect(run.out).toContain('Rev A to Rev B');
  });

  it('prints only the rate movements when asked', async () => {
    const run = await runCommand(['revisions', '--rates'], { job: revisedJob() });
    expect(run.out).toContain('Rev B:');
    expect(run.out).not.toContain('bill revisions');
  });

  it('prints the step count as json', async () => {
    const run = await runCommand(['--json', 'revisions'], { job: revisedJob() });
    expect(JSON.parse(run.out)).toBe(1);
  });
});
