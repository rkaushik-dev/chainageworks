import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, outturnFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { buildServer } from '../../src/http/server.js';
import { renderOutturn } from '../../src/reporting/outturnReport.js';
import { partsAddUp } from '../../src/valuation/outturn.js';
import { runCommand } from '../support/cliHarness.js';
import { commercialJob, fluctuatingJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(fluctuatingJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const fluctuating = () => createService(fluctuatingJob(), DEFAULT_CONFIG);
const plain = () => createService(sampleJob(), DEFAULT_CONFIG);

describe('outturnFor', () => {
  it('runs at the valuation cut off', () => {
    const outturn = outturnFor(plain(), 3);
    expect(moneyMajor(outturn.contractSum)).toBe(312_190);
    expect(partsAddUp(outturn)).toBe(true);
  });

  it('forecasts a job with nothing else on it at the bill total', () => {
    expect(moneyMajor(outturnFor(plain(), 3).total)).toBe(312_190);
  });

  it('carries every term the contract has into the account', () => {
    const outturn = outturnFor(fluctuating(), 3);
    expect(outturn.priceAdjustment.factor.lines).toHaveLength(2);
    expect(outturn.damages.minor).toBe(0);
    expect(moneyMajor(outturn.total)).toBeGreaterThan(312_190);
  });

  it('says the account rests on a provisional index', () => {
    expect(outturnFor(fluctuating(), 3).priceAdjustment.provisional).toBe(true);
    expect(outturnFor(fluctuating(), 2).priceAdjustment.provisional).toBe(false);
  });

  it('leaves the fixed price job entirely alone', () => {
    const outturn = outturnFor(createService(commercialJob(), DEFAULT_CONFIG), 3);
    expect(outturn.priceAdjustment.amount.minor).toBe(0);
    expect(moneyMajor(outturn.total)).toBe(312_190);
  });

  it('still adds up once the clause and the damages are on', () => {
    expect(partsAddUp(outturnFor(fluctuating(), 3))).toBe(true);
  });
});

describe('renderOutturn', () => {
  it('sets the parts out and then the position', () => {
    const text = renderOutturn(outturnFor(fluctuating(), 3));
    expect(text.split('\n')[0]).toBe('anticipated final account at 2025-03-25');
    expect(text).toContain('measured works');
    expect(text).toContain('liquidated damages');
    expect(text).toMatch(/parts add up +yes/);
    expect(text).toMatch(/price adjustment +on a provisional index/);
  });

  it('leaves the moving lines out when nothing has moved', () => {
    const text = renderOutturn(outturnFor(plain(), 1));
    expect(text).not.toContain('items moving away from the bill');
  });

  it('shows only the worst few moving lines', () => {
    const outturn = outturnFor(fluctuating(), 3);
    const text = renderOutturn(outturn, 2);
    const rows = text.split('\n').filter((line) => /^[A-Z]\d/.test(line));
    expect(rows.length).toBeLessThanOrEqual(2);
  });
});

describe('GET /valuations/:number/outturn', () => {
  it('answers with the parts and the position', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/outturn' })).json<{
      parts: { name: string }[];
      overContractSum: boolean;
      provisionalAdjustment: boolean;
      total: { amount: number };
    }>();
    expect(body.parts.map((p) => p.name)).toEqual([
      'measured works',
      'variations',
      'daywork',
      'price adjustment',
      'liquidated damages',
    ]);
    expect(body.overContractSum).toBe(true);
    expect(body.provisionalAdjustment).toBe(true);
    expect(body.total.amount).toBeGreaterThan(312_190);
  });

  it('gives a line for every bill item', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/outturn' })).json<{
      lines: { code: string }[];
    }>();
    expect(body.lines.length).toBeGreaterThan(0);
    expect(body.lines.every((line) => line.code.length > 0)).toBe(true);
  });
});

describe('cw outturn', () => {
  it('prints the account', async () => {
    const run = await runCommand(['outturn', '3'], { job: fluctuatingJob() });
    expect(run.code).toBe(0);
    expect(run.out.split('\n')[0]).toBe('anticipated final account at 2025-03-25');
  });

  it('prints the total in minor units as json', async () => {
    const run = await runCommand(['--json', 'outturn', '3'], { job: sampleJob() });
    expect(JSON.parse(run.out)).toBe(31_219_000);
  });
});
