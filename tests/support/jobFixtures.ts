import { buildAlignment } from '../../src/domain/chainage/alignment.js';
import { parseItemCode } from '../../src/domain/bill/itemCode.js';
import { buildDefect } from '../../src/domain/defects/defect.js';
import { buildCompletionTerms } from '../../src/domain/delay/completion.js';
import { buildDelayEvent } from '../../src/domain/delay/event.js';
import { moneyFromMajor } from '../../src/domain/money/money.js';
import { percent } from '../../src/domain/money/percent.js';
import { buildPaymentTerms } from '../../src/domain/subcontract/payment.js';
import { buildResourceReturn } from '../../src/domain/resource/return.js';
import { buildSubcontract } from '../../src/domain/subcontract/subcontract.js';
import { buildAllowances } from '../../src/progress/productivity.js';
import { parseStation } from '../../src/domain/chainage/station.js';
import { putAll } from '../../src/store/repository.js';
import type { Job } from '../../src/store/job.js';
import { createJob } from '../../src/store/job.js';
import { sampleBill } from './billFixtures.js';
import {
  d,
  range,
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

const gbp = (major: number) => moneyFromMajor(major, 'GBP');

/**
 * The sample job with the commercial side loaded as well.
 *
 * Kept apart from `sampleJob` on purpose. Most of the engine tests want a job
 * with no completion date and no defects period, because that is what a job
 * looks like before anybody argues about anything, and loading those terms
 * into every fixture would hide the null paths the reports have to handle.
 */
export function commercialJob(): Job {
  const job = sampleJob();

  job.settings = {
    ...job.settings,
    completion: buildCompletionTerms({
      baseCompletion: d('2025-09-26'),
      damagesPerDay: gbp(2500),
      damagesCap: percent(10),
    }),
    defectsPeriodEnds: d('2025-06-30'),
    outputAllowances: buildAllowances([
      { stepId: 'strip', metresPerHour: 10 },
      { stepId: 'excavate', metresPerHour: 12 },
    ]),
  };

  const gang = (count: number, hours: number) => ({
    resourceId: 'GANG-EARTH',
    description: 'earthworks gang',
    count,
    hours,
    kind: 'working' as const,
  });

  job.resourceReturns.add(
    buildResourceReturn({
      id: 'RR-01',
      packageId: 'WP-01',
      stepId: 'strip',
      date: d('2025-03-03'),
      labour: [gang(6, 8)],
      plant: [
        { resourceId: 'PL-D6', description: 'dozer', count: 1, hours: 8, kind: 'working' },
      ],
      returnedBy: 'A Kerrigan',
    }),
  );
  job.resourceReturns.add(
    buildResourceReturn({
      id: 'RR-02',
      packageId: 'WP-01',
      stepId: 'strip',
      date: d('2025-03-04'),
      labour: [gang(6, 8)],
      returnedBy: 'A Kerrigan',
    }),
  );
  job.resourceReturns.add(
    buildResourceReturn({
      id: 'RR-03',
      packageId: 'WP-01',
      stepId: 'excavate',
      date: d('2025-03-10'),
      labour: [gang(4, 10)],
      plant: [
        {
          resourceId: 'PL-360',
          description: '22t excavator',
          count: 1,
          hours: 4,
          kind: 'standing',
        },
      ],
      returnedBy: 'A Kerrigan',
    }),
  );
  job.resourceReturns.add(
    buildResourceReturn({
      id: 'RR-04',
      packageId: 'WP-01',
      date: d('2025-03-11'),
      labour: [gang(2, 8)],
      returnedBy: 'A Kerrigan',
      note: 'general site tidy, no step named',
    }),
  );

  job.delayEvents.add(
    buildDelayEvent({
      id: 'DE-01',
      reference: 'EW/2025/003',
      cause: 'utilities',
      description: 'live water main outside its recorded position',
      from: d('2025-03-03'),
      to: d('2025-03-14'),
      notifiedOn: d('2025-03-05'),
      awardedDays: 8,
    }),
  );
  job.delayEvents.add(
    buildDelayEvent({
      id: 'DE-02',
      reference: 'EW/2025/004',
      cause: 'design',
      description: 'revised drainage layout issued late',
      from: d('2025-03-10'),
      to: d('2025-03-21'),
      notifiedOn: null,
    }),
  );

  job.defects.add(
    buildDefect({
      id: 'DF-01',
      reference: 'DEF/2025/001',
      packageId: 'WP-03',
      extent: range('12+400', '12+600'),
      description: 'surface course ravelling at the joint',
      raisedOn: d('2025-03-03'),
      dueBy: d('2025-03-17'),
    }),
  );
  job.defects.add(
    buildDefect({
      id: 'DF-02',
      reference: 'DEF/2025/002',
      packageId: 'WP-01',
      description: 'missing barrier at the retaining wall',
      severity: 'safety',
      raisedOn: d('2025-03-20'),
      dueBy: d('2025-03-21'),
    }),
  );

  job.subcontracts.add(
    buildSubcontract({
      id: 'SC-EARTH',
      name: 'bulk earthworks and capping',
      contractor: 'Dowsett Plant Hire',
      orderValue: gbp(250_000),
      packageIds: ['WP-01', 'WP-02'],
      codes: [parseItemCode('E423.1')],
      placedOn: d('2024-11-04'),
    }),
  );
  job.subcontracts.add(
    buildSubcontract({
      id: 'SC-FENCE',
      name: 'permanent fencing',
      contractor: 'Marchmont Fencing',
      orderValue: gbp(31_000),
      packageIds: ['WP-03'],
      terms: buildPaymentTerms({ dueDays: 21, retention: percent(3) }),
      placedOn: d('2025-01-13'),
    }),
  );

  job.paymentApplications.add({
    id: 'SA-01',
    subcontractId: 'SC-EARTH',
    periodNumber: 1,
    appliedOn: d('2025-01-25'),
    gross: gbp(40_000),
    previouslyPaid: gbp(0),
  });
  job.paymentApplications.add({
    id: 'SA-02',
    subcontractId: 'SC-EARTH',
    periodNumber: 2,
    appliedOn: d('2025-02-25'),
    gross: gbp(90_000),
    previouslyPaid: gbp(38_000),
  });
  job.paymentNotices.add({
    applicationId: 'SA-02',
    servedOn: d('2025-03-28'),
    valued: gbp(85_000),
  });
  return job;
}
