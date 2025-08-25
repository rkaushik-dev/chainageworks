import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, variationsFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { buildServer } from '../../src/http/server.js';
import {
  chaseAgreement,
  renderVariationReport,
  touchedItems,
} from '../../src/reporting/variationReport.js';
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

describe('variationsFor', () => {
  const report = variationsFor(service(), 3);

  it('lists every order on the register, in force or not', () => {
    expect(report.lines.map((line) => line.variation.id)).toEqual(['VO-01', 'VO-02', 'VO-03']);
  });

  it('values only the ones in force', () => {
    expect(report.lines[0]!.inForce).toBe(true);
    expect(report.lines[2]!.inForce).toBe(false);
    expect(report.lines[2]!.value.minor).toBe(0);
  });

  it('values each order against the unvaried bill', () => {
    expect(moneyMajor(report.lines[0]!.value)).toBe(2425);
  });

  it('reprices the whole item when an order is priced pro rata', () => {
    // The pro rata rate applies to everything under that code, not only to
    // the extra quantity, which is what makes the second order so much
    // larger than the first.
    expect(moneyMajor(report.lines[1]!.value)).toBe(63_130);
  });

  it('adds the ones in force up', () => {
    expect(moneyMajor(report.inForce)).toBe(65_555);
  });

  it('keeps what nobody has agreed apart from the total', () => {
    expect(moneyMajor(report.unagreed)).toBe(2425);
  });

  it('counts the proposed orders rather than valuing them', () => {
    expect(report.proposed).toBe(1);
  });

  it('names the ways each order is priced', () => {
    expect(report.lines[0]!.bases).toEqual(['bill_rates']);
    expect(report.lines[1]!.bases).toEqual(['pro_rata']);
  });

  it('is empty on a job with no orders', () => {
    const bare = variationsFor(createService(sampleJob(), DEFAULT_CONFIG), 3);
    expect(bare.lines).toEqual([]);
    expect(bare.inForce.minor).toBe(0);
    expect(bare.proposed).toBe(0);
  });

  it('leaves out an order instructed after the cut off', () => {
    const early = variationsFor(service(), 1);
    expect(early.lines.every((line) => !line.inForce)).toBe(true);
    expect(early.inForce.minor).toBe(0);
  });
});

describe('chaseAgreement', () => {
  it('names the orders in force with no agreed price, dearest first', () => {
    const chase = chaseAgreement(variationsFor(service(), 3));
    expect(chase).toHaveLength(1);
    expect(chase[0]).toContain('VO/2025/001');
    expect(chase[0]).toContain('not agreed');
  });
});

describe('touchedItems', () => {
  it('lists what the orders in force move, for the remeasure', () => {
    expect(touchedItems(variationsFor(service(), 3))).toEqual([
      'E423.1 500.00 m3',
      'E532.1 200.00 m3',
    ]);
  });
});

describe('renderVariationReport', () => {
  it('opens with the totals and tables the register', () => {
    const text = renderVariationReport(variationsFor(service(), 3));
    expect(text.split('\n')[0]).toBe('variations at 2025-03-25');
    expect(text).toMatch(/orders on the register +3/);
    expect(text).toMatch(/proposed, not instructed +1/);
    expect(text).toContain('12+300.000 to 12+500.000');
  });

  it('leaves the table out on a job with no orders', () => {
    const text = renderVariationReport(
      variationsFor(createService(sampleJob(), DEFAULT_CONFIG), 3),
    );
    expect(text).not.toMatch(/^order +title +status/m);
    expect(text).toMatch(/orders on the register +0/);
  });
});

describe('GET /valuations/:number/variations', () => {
  it('answers with the register and the chase list', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/variations' })).json<{
      orders: { id: string; inForce: boolean }[];
      inForce: { amount: number };
      proposed: number;
      chase: string[];
      touchedItems: string[];
    }>();
    expect(body.orders).toHaveLength(3);
    expect(body.inForce.amount).toBe(65_555);
    expect(body.proposed).toBe(1);
    expect(body.chase).toHaveLength(1);
    expect(body.touchedItems).toHaveLength(2);
  });
});

describe('cw variations', () => {
  it('prints the register', async () => {
    const run = await runCommand(['variations', '3'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(run.out).toContain('VO/2025/002');
  });

  it('prints the order count as json', async () => {
    const run = await runCommand(['--json', 'variations', '3'], { job: commercialJob() });
    expect(JSON.parse(run.out)).toBe(3);
  });
});
