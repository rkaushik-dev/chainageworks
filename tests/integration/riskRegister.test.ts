import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, risksFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { unowned } from '../../src/domain/risk/earlyWarning.js';
import { buildServer } from '../../src/http/server.js';
import { meetingsToArrange, renderRiskReport } from '../../src/reporting/riskReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(commercialJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('risksFor', () => {
  const position = risksFor(service(), 3);

  it('takes the register at the valuation cut off', () => {
    expect(position.open.map((w) => w.id)).toEqual(['EW-01', 'EW-02']);
  });

  it('keeps the expected cost apart from the worst case', () => {
    expect(moneyMajor(position.expected)).toBe(39_000);
    expect(moneyMajor(position.worstCase)).toBe(80_000);
  });

  it('counts only what the contractor carries', () => {
    expect(moneyMajor(position.carried)).toBe(15_000);
  });

  it('names the meeting nobody held', () => {
    expect(position.overdueMeetings.map((w) => w.id)).toEqual(['EW-02']);
  });

  it('adds the working days at risk', () => {
    expect(position.delayDays).toBe(17);
  });

  it('leaves out a warning raised after the cut off', () => {
    expect(risksFor(service(), 1).open).toEqual([]);
  });

  it('is empty on a job with no register', () => {
    const bare = risksFor(createService(sampleJob(), DEFAULT_CONFIG), 3);
    expect(bare.open).toEqual([]);
    expect(bare.expected.minor).toBe(0);
    expect(unowned(bare)).toEqual([]);
  });
});

describe('renderRiskReport', () => {
  it('opens with the totals and tables the register by expected cost', () => {
    const text = renderRiskReport(risksFor(service(), 3));
    expect(text.split('\n')[0]).toBe('early warnings at 2025-03-25');
    expect(text).toMatch(/expected cost +£39,000\.00/);
    expect(text).toMatch(/meetings overdue +1/);
    expect(text).toContain('12+400.000 to 12+600.000');
  });

  it('leaves the table out on an empty register', () => {
    const text = renderRiskReport(risksFor(createService(sampleJob(), DEFAULT_CONFIG), 3));
    expect(text).toMatch(/open +0/);
    expect(text).not.toContain('if it happens');
  });
});

describe('meetingsToArrange', () => {
  it('names the meetings somebody has to arrange', () => {
    const chase = meetingsToArrange(risksFor(service(), 3));
    expect(chase).toEqual(['EW/2025/002 raised 2025-02-20, no meeting held']);
  });
});

describe('GET /valuations/:number/risks', () => {
  it('answers with the register and what it might cost', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/risks' })).json<{
      warnings: { id: string; expected: { amount: number } }[];
      expected: { amount: number };
      overdueMeetings: string[];
      unowned: string[];
    }>();
    expect(body.warnings).toHaveLength(2);
    expect(body.expected.amount).toBe(39_000);
    expect(body.warnings[0]!.expected.amount).toBe(24_000);
    expect(body.overdueMeetings).toEqual(['EW-02']);
    expect(body.unowned).toEqual([]);
  });

  it('counts the register on the job', async () => {
    const body = (await app.inject({ method: 'GET', url: '/job' })).json<
      Record<string, unknown>
    >();
    expect(body['counts']).toMatchObject({ earlyWarnings: 2 });
  });
});

describe('cw risks', () => {
  it('prints the register', async () => {
    const run = await runCommand(['risks', '3'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(run.out).toContain('EW/2025/001');
  });

  it('prints the open count as json', async () => {
    const run = await runCommand(['--json', 'risks', '3'], { job: commercialJob() });
    expect(JSON.parse(run.out)).toBe(2);
  });
});

describe('the risk register through the job file', () => {
  it('round trips the warnings and the meeting window', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(commercialJob()))) as unknown,
    );
    expect(again.earlyWarnings.count()).toBe(2);
    expect(again.earlyWarnings.require('EW-01').owner).toBe('employer');
    expect(again.earlyWarnings.require('EW-01').extent).not.toBeNull();
    expect(again.earlyWarnings.require('EW-02').meetingOn).toBeNull();
    expect(again.settings.riskMeetingDays).toBe(14);
  });

  it('gives the same position off the reloaded job', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(commercialJob()))) as unknown,
    );
    expect(moneyMajor(risksFor(createService(again, DEFAULT_CONFIG), 3).expected)).toBe(39_000);
  });

  it('carries a job with no register through as empty', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.earlyWarnings.count()).toBe(0);
  });
});

describe('cw doctor with a register', () => {
  it('still finds nothing wrong', async () => {
    const run = await runCommand(['doctor'], { job: commercialJob() });
    expect(run.code).toBe(0);
  });
});
