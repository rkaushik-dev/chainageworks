import { buildAlignment } from '../../src/domain/chainage/alignment.js';
import { moneyFromMajor } from '../../src/domain/money/money.js';
import { parseStation } from '../../src/domain/chainage/station.js';
import { putAll } from '../../src/store/repository.js';
import type { Job } from '../../src/store/job.js';
import { createJob } from '../../src/store/job.js';
import { sampleBill } from './billFixtures.js';
import {
  d,
  earthworksPlan,
  pavementPlan,
  sampleNcrs,
  sampleRecords,
  sampleSignOffs,
} from './contextFixtures.js';
import { samplePackages } from './packageFixtures.js';

/** A job loaded with the sample bill, packages, plans and site records. */
export function sampleJob(): Job {
  const job = createJob({
    id: 'A1-IMP',
    name: 'A1 improvement, section 2',
    contractSum: moneyFromMajor(312_190, 'GBP'),
  });

  job.alignments.add(
    buildAlignment({
      id: 'A1',
      name: 'main carriageway',
      start: parseStation('12+000'),
      end: parseStation('14+000'),
    }),
  );
  job.billRevisions.add({
    reference: 'Rev A',
    effectiveFrom: d('2025-01-01'),
    bill: sampleBill(),
    note: 'contract bill at award',
  });
  putAll(job.packages, samplePackages());
  putAll(job.plans, [earthworksPlan, pavementPlan]);
  putAll(job.signOffs, sampleSignOffs());
  putAll(job.ncrs, sampleNcrs());
  putAll(job.records, sampleRecords());
  return job;
}

/** A job with the settings and nothing loaded, for the blocker checks. */
export function emptyJob(): Job {
  return createJob({
    id: 'A1-IMP',
    name: 'A1 improvement, section 2',
    contractSum: moneyFromMajor(312_190, 'GBP'),
  });
}
