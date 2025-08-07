import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { certificateFor, certificateSeries, createService } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { moneyFromMajor, moneyMajor } from '../../src/domain/money/money.js';
import { percent } from '../../src/domain/money/percent.js';
import { buildServer } from '../../src/http/server.js';
import { renderCertificate } from '../../src/reporting/format/text.js';
import { bondHasLapsed, bondReleased } from '../../src/valuation/certificate.js';
import { buildRetentionBond } from '../../src/valuation/bond.js';
import { jobFromJson, jobToJson } from '../../src/store/codec/job.js';
import type { Job } from '../../src/store/job.js';
import { runCommand } from '../support/cliHarness.js';
import { d } from '../support/contextFixtures.js';
import { sampleJob } from '../support/jobFixtures.js';

const gbp = (major: number) => moneyFromMajor(major, 'GBP');

function bondedJob(): Job {
  const job = sampleJob();
  job.settings = {
    ...job.settings,
    retentionBond: buildRetentionBond({
      id: 'RB-01',
      surety: 'Northgate Surety',
      maximum: gbp(9365),
      issuedOn: d('2024-11-01'),
      expiresOn: d('2026-11-01'),
      rate: percent(1.2),
    }),
  };
  return job;
}

function lapsedJob(): Job {
  const job = sampleJob();
  job.settings = {
    ...job.settings,
    retentionBond: buildRetentionBond({
      id: 'RB-02',
      surety: 'Northgate Surety',
      maximum: gbp(9365),
      issuedOn: d('2024-11-01'),
      expiresOn: d('2025-02-01'),
    }),
  };
  return job;
}

let app: FastifyInstance;

beforeAll(() => {
  app = buildServer(createService(bondedJob(), DEFAULT_CONFIG));
});

afterAll(async () => {
  await app.close();
});

const bonded = () => createService(bondedJob(), DEFAULT_CONFIG);
const cash = () => createService(sampleJob(), DEFAULT_CONFIG);

describe('a certificate with a retention bond', () => {
  it('still works the retention out the same way', () => {
    expect(moneyMajor(certificateFor(bonded(), 3).retention.held)).toBe(
      moneyMajor(certificateFor(cash(), 3).retention.held),
    );
  });

  it('holds no cash while the bond covers it', () => {
    const certificate = certificateFor(bonded(), 3);
    expect(certificate.retentionHeld.minor).toBe(0);
    expect(moneyMajor(bondReleased(certificate))).toBe(807.63);
  });

  it('pays the gross valuation across rather than the net of retention', () => {
    const certificate = certificateFor(bonded(), 3);
    expect(moneyMajor(certificate.netValuation)).toBe(moneyMajor(certificate.gross));
    expect(moneyMajor(certificate.amountDue)).toBe(16_152.5);
  });

  it('pays more than the cash retention job does', () => {
    expect(moneyMajor(certificateFor(bonded(), 3).amountDue)).toBeGreaterThan(
      moneyMajor(certificateFor(cash(), 3).amountDue),
    );
  });

  it('reports how long the bond has left', () => {
    expect(certificateFor(bonded(), 3).bond!.daysToExpiry).toBeGreaterThan(0);
    expect(bondHasLapsed(certificateFor(bonded(), 3))).toBe(false);
  });
});

describe('a certificate after the bond has lapsed', () => {
  it('goes back to holding the cash', () => {
    const certificate = certificateFor(createService(lapsedJob(), DEFAULT_CONFIG), 3);
    expect(bondHasLapsed(certificate)).toBe(true);
    expect(moneyMajor(certificate.retentionHeld)).toBe(807.63);
    expect(certificate.bond!.released.minor).toBe(0);
  });

  it('covered the cash while it was still in force', () => {
    const early = certificateFor(createService(lapsedJob(), DEFAULT_CONFIG), 1);
    expect(bondHasLapsed(early)).toBe(false);
  });
});

describe('a job with no bond at all', () => {
  it('reports no bond position and holds the cash', () => {
    const certificate = certificateFor(cash(), 3);
    expect(certificate.bond).toBeNull();
    expect(moneyMajor(certificate.retentionHeld)).toBe(807.63);
    expect(bondReleased(certificate).minor).toBe(0);
  });
});

describe('a series with a bond', () => {
  it('carries the same net figure forward each month', () => {
    const series = certificateSeries(bonded(), 4);
    for (const [index, certificate] of series.entries()) {
      const previous = index === 0 ? 0 : moneyMajor(series[index - 1]!.netValuation);
      expect(moneyMajor(certificate.previouslyCertified)).toBe(previous);
    }
  });
});

describe('renderCertificate with a bond', () => {
  it('prints the retention due and what the bond covers', () => {
    const text = renderCertificate(certificateFor(bonded(), 3));
    expect(text).toContain('retention due');
    expect(text).toContain('covered by bond RB-01');
    expect(text).toMatch(/less retention +£0\.00/);
  });

  it('leaves both lines off a cash retention job', () => {
    const text = renderCertificate(certificateFor(cash(), 3));
    expect(text).not.toContain('retention due');
    expect(text).not.toContain('covered by bond');
  });
});

describe('GET /valuations/:number/certificate with a bond', () => {
  it('carries the bond position', async () => {
    const body = (await app.inject({ method: 'GET', url: '/valuations/3/certificate' })).json<{
      bond: { id: string; released: { amount: number }; expired: boolean } | null;
      retentionHeld: { amount: number };
    }>();
    expect(body.bond!.id).toBe('RB-01');
    expect(body.bond!.released.amount).toBe(807.63);
    expect(body.bond!.expired).toBe(false);
    expect(body.retentionHeld.amount).toBe(0);
  });
});

describe('the bond through the job file', () => {
  it('round trips the bond and its terms', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(bondedJob()))) as unknown);
    expect(again.settings.retentionBond!.id).toBe('RB-01');
    expect(again.settings.retentionBond!.surety).toBe('Northgate Surety');
    expect(moneyMajor(again.settings.retentionBond!.maximum)).toBe(9365);
    expect(again.settings.retentionBond!.reducesTo).toBeNull();
  });

  it('carries a job with no bond through as null', () => {
    const again = jobFromJson(JSON.parse(JSON.stringify(jobToJson(sampleJob()))) as unknown);
    expect(again.settings.retentionBond).toBeNull();
  });
});

describe('cw certificate with a bond', () => {
  it('prints the bond line', async () => {
    const run = await runCommand(['certificate', '3'], { job: bondedJob() });
    expect(run.code).toBe(0);
    expect(run.out).toContain('covered by bond RB-01');
  });
});
