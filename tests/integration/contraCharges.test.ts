import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createService, paymentsFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyMajor } from '../../src/domain/money/money.js';
import { buildContraCharge } from '../../src/domain/subcontract/contraCharge.js';
import { buildServer } from '../../src/http/server.js';
import { renderSubcontractReport } from '../../src/reporting/subcontractReport.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import { runCommand } from '../support/cliHarness.js';
import { d } from '../support/contextFixtures.js';
import { commercialJob, sampleJob } from '../support/jobFixtures.js';

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(commercialJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const service = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('the contra position on a payment report', () => {
  it('sets off what the order covers and what was notified in time', () => {
    const earth = paymentsFor(service(), 3).positions[0]!;
    expect(moneyMajor(earth.contra.raised)).toBe(6950);
    expect(moneyMajor(earth.contra.deductible)).toBe(6000);
  });

  it('bars the charge nobody notified once its window has shut', () => {
    const earth = paymentsFor(service(), 3).positions[0]!;
    expect(moneyMajor(earth.contra.barred)).toBe(950);
  });

  it('reports the disputed part of what is being taken', () => {
    const earth = paymentsFor(service(), 3).positions[0]!;
    expect(moneyMajor(earth.contra.disputed)).toBe(4200);
  });

  it('takes the set off out of what actually leaves the bank', () => {
    const earth = paymentsFor(service(), 3).positions[0]!;
    expect(moneyMajor(earth.paidToDate)).toBe(80_750);
    expect(moneyMajor(earth.netOfContra)).toBe(74_750);
    expect(earth.contraCarriedForward.minor).toBe(0);
  });

  it('leaves the certified figure alone', () => {
    const earth = paymentsFor(service(), 3).positions[0]!;
    expect(moneyMajor(earth.certifiedToDate)).toBe(80_750);
    expect(moneyMajor(earth.remaining)).toBe(169_250);
  });

  it('charges nothing against an order nobody has charged', () => {
    const fence = paymentsFor(service(), 3).positions[1]!;
    expect(fence.contra.charges).toEqual([]);
    expect(fence.netOfContra.minor).toBe(0);
  });

  it('names the orders carrying barred set off', () => {
    expect(paymentsFor(service(), 3).barredContra).toEqual([
      { subcontractId: 'SC-EARTH', amount: '£950.00' },
    ]);
  });

  it('carries the rest forward when the set off is larger than the payment', () => {
    const job = commercialJob();
    job.contraCharges.add(
      buildContraCharge({
        id: 'CC-99',
        subcontractId: 'SC-EARTH',
        reason: 'welfare',
        description: 'cabins and drying room for the whole gang',
        amount: { minor: 12_000_000, currency: 'GBP' },
        incurredOn: d('2025-03-02'),
      }),
    );
    const earth = paymentsFor(createService(job, DEFAULT_CONFIG), 3).positions[0]!;
    expect(earth.netOfContra.minor).toBe(0);
    expect(moneyMajor(earth.contraCarriedForward)).toBe(45_250);
  });

  it('leaves out a charge incurred after the cut off', () => {
    const job = commercialJob();
    job.contraCharges.add(
      buildContraCharge({
        id: 'CC-98',
        subcontractId: 'SC-EARTH',
        reason: 'welfare',
        description: 'cabins for april',
        amount: { minor: 100_000, currency: 'GBP' },
        incurredOn: d('2025-04-02'),
      }),
    );
    const earth = paymentsFor(createService(job, DEFAULT_CONFIG), 3).positions[0]!;
    expect(moneyMajor(earth.contra.raised)).toBe(6950);
  });
});

describe('renderSubcontractReport with set off', () => {
  it('tables the set off and the net alongside the paid figure', () => {
    const text = renderSubcontractReport(paymentsFor(service(), 3));
    expect(text).toMatch(/set off barred +1/);
    expect(text).toContain('set off');
    expect(text).toContain('£6,000.00');
  });
});

describe('GET /valuations/:number/payments with set off', () => {
  it('carries the contra position and the barred list', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/payments' })).json<{
      barredContra: { subcontractId: string }[];
      orders: {
        contra: { deductible: { amount: number }; charges: { id: string }[] };
        netOfContra: { amount: number };
      }[];
    }>();
    expect(body.barredContra).toEqual([{ subcontractId: 'SC-EARTH', amount: '£950.00' }]);
    expect(body.orders[0]!.contra.deductible.amount).toBe(6000);
    expect(body.orders[0]!.contra.charges.map((c) => c.id)).toEqual([
      'CC-01',
      'CC-03',
      'CC-02',
    ]);
    expect(body.orders[0]!.netOfContra.amount).toBe(74_750);
  });
});

describe('cw payments with set off', () => {
  it('prints the set off column', async () => {
    const run = await runCommand(['payments', '3'], { job: commercialJob() });
    expect(run.code).toBe(0);
    expect(run.out).toContain('£74,750.00');
  });
});

describe('the contra charges through the job file', () => {
  it('round trips the charges and the notice window', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(commercialJob()))) as unknown,
    );
    expect(again.contraCharges.count()).toBe(3);
    expect(again.contraCharges.require('CC-02').disputed).toBe(true);
    expect(again.contraCharges.require('CC-03').notifiedOn).toBeNull();
    expect(again.settings.contraNotifyDays).toBe(14);
  });

  it('gives the same position off the reloaded job', () => {
    const again = jobFromJson(
      JSON.parse(JSON.stringify(jobToJson(commercialJob()))) as unknown,
    );
    const earth = paymentsFor(createService(again, DEFAULT_CONFIG), 3).positions[0]!;
    expect(moneyMajor(earth.netOfContra)).toBe(74_750);
  });

  it('carries a job with no charges through as empty', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.contraCharges.count()).toBe(0);
  });
});
