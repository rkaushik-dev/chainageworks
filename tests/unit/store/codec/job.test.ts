import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import { billTotal } from '../../../../src/domain/bill/bill.js';
import { alignmentLength } from '../../../../src/domain/chainage/alignment.js';
import { moneyFromMajor, moneyMajor } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import { quantity } from '../../../../src/domain/quantity/quantity.js';
import { rate } from '../../../../src/domain/money/rate.js';
import { parseItemCode } from '../../../../src/domain/bill/itemCode.js';
import { buildVariation } from '../../../../src/domain/variation/variation.js';
import { buildMaterialsOnSite } from '../../../../src/valuation/materials.js';
import { earnAll } from '../../../../src/progress/earn.js';
import { jobContext } from '../../../../src/store/job.js';
import { DOCUMENT_VERSION, jobFromJson, jobToJson } from '../../../../src/store/codec/job.js';
import { d } from '../../../support/contextFixtures.js';
import { sampleJob } from '../../../support/jobFixtures.js';

const through = (job = sampleJob()) =>
  jobFromJson(JSON.parse(JSON.stringify(jobToJson(job))) as unknown);

describe('jobToJson', () => {
  const doc = jobToJson(sampleJob());

  it('stamps the document version', () => {
    expect(doc.version).toBe(DOCUMENT_VERSION);
  });

  it('writes every register', () => {
    expect(doc.alignments).toHaveLength(1);
    expect(doc.billRevisions).toHaveLength(1);
    expect(doc.packages).toHaveLength(3);
    expect(doc.profiles).toHaveLength(7);
    expect(doc.plans).toHaveLength(2);
    expect(doc.signOffs).toHaveLength(2);
    expect(doc.ncrs).toHaveLength(1);
    expect(doc.records).toHaveLength(3);
  });

  it('survives JSON.stringify', () => {
    expect(() => JSON.stringify(doc)).not.toThrow();
  });
});

describe('jobFromJson', () => {
  it('brings back the settings', () => {
    const job = through();
    expect(job.settings.id).toBe('A1-IMP');
    expect(moneyMajor(job.settings.contractSum)).toBe(312_190);
    expect(job.settings.cutOffDay).toBe(25);
    expect(job.settings.workingDays).toEqual([1, 2, 3, 4, 5]);
  });

  it('brings back the alignment with its length intact', () => {
    expect(alignmentLength(through().alignments.require('A1'))).toBe(2_000_000);
  });

  it('brings back the bill exactly', () => {
    const job = through();
    const revision = job.billRevisions.require('Rev A');
    expect(moneyMajor(billTotal(revision.bill))).toBe(312_190);
    expect(revision.bill.items).toHaveLength(8);
  });

  it('brings back the packages and their allocations', () => {
    const job = through();
    expect(job.packages.count()).toBe(3);
    expect(job.packages.require('WP-01').allocations).toHaveLength(2);
    expect(job.packages.require('WP-03').profileId).toBe('pavement');
  });

  it('brings back the plans against the rules of credit they gate', () => {
    const job = through();
    expect(job.plans.require('ITP-EW').points).toHaveLength(3);
    expect(job.plans.require('ITP-EW').points[0]?.type).toBe('hold');
  });

  it('brings back the site records, sign offs and reports', () => {
    const job = through();
    expect(job.records.require('SR-02').stepId).toBe('excavate');
    expect(job.signOffs.require('SO-01').by).toBe('R Engineer');
    expect(job.ncrs.require('NCR-01').withholdsCredit).toBe(true);
  });

  it('values the same after a round trip, which is the whole point', () => {
    const before = earnAll(jobContext(sampleJob()), d('2025-03-31'));
    const after = earnAll(jobContext(through()), d('2025-03-31'));
    expect(after.map((e) => e.earnedValue)).toEqual(before.map((e) => e.earnedValue));
    expect(moneyMajor(after[0]!.earnedValue)).toBe(16_152.5);
  });

  it('carries the holidays and the standdowns', () => {
    const job = sampleJob();
    job.holidays = [d('2025-04-18')];
    job.standdowns = [{ date: d('2025-03-05'), reason: 'weather', note: 'rain' }];
    const again = through(job);
    expect(again.holidays).toEqual([d('2025-04-18')]);
    expect(again.standdowns[0]?.reason).toBe('weather');
    expect(again.standdowns[0]?.note).toBe('rain');
  });

  it('carries materials and variations', () => {
    const job = sampleJob();
    job.materials.add(
      buildMaterialsOnSite({
        id: 'MOS-01',
        packageId: 'WP-02',
        code: parseItemCode('E423.1'),
        description: 'imported fill',
        quantity: quantity(1200, 'm3'),
        value: moneyFromMajor(9000, 'GBP'),
        deliveredOn: d('2025-03-10'),
        vestedOn: d('2025-03-12'),
      }),
    );
    job.variations.add(
      buildVariation({
        id: 'VO-012',
        reference: 'EI-045',
        title: 'deepen the cutting',
        status: 'instructed',
        instructedOn: d('2025-03-14'),
        items: [
          {
            code: parseItemCode('X400.1'),
            quantityChange: quantity(40, 'm3'),
            rate: rate(94, 'GBP', 'm3'),
            basis: 'star_rate',
          },
        ],
      }),
    );
    const again = through(job);
    expect(again.materials.require('MOS-01').description).toBe('imported fill');
    expect(again.variations.require('VO-012').items[0]?.basis).toBe('star_rate');
  });

  it('keeps a job with different settings', () => {
    const job = sampleJob();
    job.settings = {
      ...job.settings,
      materialsAllowance: percent(85),
      workingDays: [1, 2, 3, 4, 5, 6],
      substantialCompletionOn: d('2025-11-01'),
    };
    const again = through(job);
    expect(again.settings.materialsAllowance).toEqual(percent(85));
    expect(again.settings.workingDays).toEqual([1, 2, 3, 4, 5, 6]);
    expect(again.settings.substantialCompletionOn).toBe(d('2025-11-01'));
  });

  it('refuses a document from another version', () => {
    const doc = { ...jobToJson(sampleJob()), version: 99 };
    expect(() => jobFromJson(doc)).toThrow(/written by a different version/);
  });

  it('refuses something that is not a document at all', () => {
    expect(() => jobFromJson('nonsense')).toThrow(/expected an object/);
    expect(() => jobFromJson(null)).toThrow(BadInputError);
  });

  it('refuses a plan that gates a rule of credit the job does not carry', () => {
    const doc = jobToJson(sampleJob());
    const broken = {
      ...doc,
      profiles: doc.profiles.filter((p) => p['id'] !== 'bulk-earthworks'),
    };
    expect(() => jobFromJson(broken)).toThrow(/does not carry/);
  });

  it('refuses a record whose item code is nonsense', () => {
    const doc = jobToJson(sampleJob());
    const broken = {
      ...doc,
      records: [{ ...doc.records[0], code: '423' }, ...doc.records.slice(1)],
    };
    expect(() => jobFromJson(broken)).toThrow(BadInputError);
  });
});
