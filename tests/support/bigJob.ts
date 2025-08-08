import { buildBill } from '../../src/domain/bill/bill.js';
import { buildBillItem } from '../../src/domain/bill/billItem.js';
import { itemCode } from '../../src/domain/bill/itemCode.js';
import type { ItemCode } from '../../src/domain/bill/itemCode.js';
import { buildAlignment } from '../../src/domain/chainage/alignment.js';
import { chainageRange } from '../../src/domain/chainage/range.js';
import { asStation } from '../../src/domain/chainage/station.js';
import { BULK_EARTHWORKS } from '../../src/domain/credit/catalogue.js';
import { buildInspectionPlan } from '../../src/domain/itp/plan.js';
import { buildSignOff } from '../../src/domain/itp/signOff.js';
import { buildNcr } from '../../src/domain/itp/ncr.js';
import { moneyFromMajor } from '../../src/domain/money/money.js';
import { rate } from '../../src/domain/money/rate.js';
import { buildWorkPackage } from '../../src/domain/packages/workPackage.js';
import { quantity } from '../../src/domain/quantity/quantity.js';
import { buildResourceReturn } from '../../src/domain/resource/return.js';
import { buildContraCharge } from '../../src/domain/subcontract/contraCharge.js';
import { buildSubcontract } from '../../src/domain/subcontract/subcontract.js';
import { buildSurveyRecord } from '../../src/domain/survey/record.js';
import { buildAllowances } from '../../src/progress/productivity.js';
import { addDays } from '../../src/domain/time/siteDate.js';
import { createJob } from '../../src/store/job.js';
import type { Job } from '../../src/store/job.js';
import { putAll } from '../../src/store/repository.js';
import { d } from './contextFixtures.js';
import type { Rng } from './random.js';
import { rng } from './random.js';

export interface BigJobOptions {
  /** Packages down the alignment. Each is a kilometre. */
  readonly packages?: number;
  /** Survey records booked per package. */
  readonly recordsPerPackage?: number;
  /** Subcontract orders across the job, each with monthly applications. */
  readonly subcontracts?: number;
  /** Daily resource returns booked against the packages. */
  readonly resourceReturns?: number;
  readonly seed?: number;
}

/**
 * A job the size of a real contract, built from a seed.
 *
 * Two hundred packages and eight thousand records is a two year highway job,
 * and it is the size at which an accidentally quadratic lookup in the engine
 * stops being invisible. The generator is seeded so a timing regression can be
 * reproduced rather than argued about.
 */
export function bigJob(options: BigJobOptions = {}): Job {
  const packageCount = options.packages ?? 200;
  const perPackage = options.recordsPerPackage ?? 40;
  const random = rng(options.seed ?? 20_250_301);

  const job = createJob({
    id: 'BIG',
    name: 'a job the size of a real one',
    contractSum: moneyFromMajor(50_000_000, 'GBP'),
  });

  job.alignments.add(
    buildAlignment({
      id: 'A1',
      name: 'main carriageway',
      start: asStation(0),
      end: asStation(packageCount * 1_000_000),
    }),
  );

  const codes: ItemCode[] = [
    itemCode('E', [4, 2, 3], '1'),
    itemCode('E', [5, 3, 2], '1'),
    itemCode('E', [4, 2, 4], '1'),
  ];

  const bill = buildBill({
    id: 'Rev A',
    title: 'bill of quantities',
    currency: 'GBP',
    items: codes.map((code, index) =>
      buildBillItem({
        code,
        description: `bulk earthworks item ${String(index + 1)}`,
        rate: rate(4 + index, 'GBP', 'm3'),
        quantity: quantity(packageCount * 5000, 'm3'),
      }),
    ),
  });
  job.billRevisions.add({
    reference: 'Rev A',
    effectiveFrom: d('2025-01-01'),
    bill,
    note: 'contract bill at award',
  });

  job.plans.add(
    buildInspectionPlan(
      {
        id: 'ITP-EW',
        name: 'earthworks',
        profileId: 'bulk-earthworks',
        points: [
          { id: 'IP-E1', name: 'formation level', type: 'hold', stepId: 'excavate' },
          { id: 'IP-E2', name: 'layer density', type: 'hold', stepId: 'place' },
        ],
      },
      BULK_EARTHWORKS,
    ),
  );

  const packages = [];
  const records = [];
  const signOffs = [];
  const ncrs = [];

  for (let i = 0; i < packageCount; i += 1) {
    const from = asStation(i * 1_000_000);
    const to = asStation((i + 1) * 1_000_000);
    const id = `WP-${String(i + 1).padStart(4, '0')}`;
    packages.push(
      buildWorkPackage({
        id,
        name: `earthworks package ${id}`,
        alignmentId: 'A1',
        extent: chainageRange(from, to),
        section: `section ${String(Math.floor(i / 20) + 1)}`,
        profileId: 'bulk-earthworks',
        allocations: codes.map((code) => ({ code, quantity: quantity(5000, 'm3') })),
      }),
    );

    for (let r = 0; r < perPackage; r += 1) {
      const step = random.pick(['strip', 'excavate', 'place', 'trim'] as const);
      const start = random.int(0, 900) * 1000;
      records.push(
        buildSurveyRecord({
          id: `${id}-SR-${String(r).padStart(3, '0')}`,
          packageId: id,
          code: random.pick(codes),
          stepId: step,
          extent: chainageRange(
            asStation(from + start),
            asStation(from + start + random.int(20, 100) * 1000),
          ),
          quantity: quantity(random.int(10, 400), 'm3'),
          measuredOn: addDays(d('2025-01-05'), random.int(0, 500)),
          bookedBy: 'J Foreman',
        }),
      );
    }

    signOffs.push(
      buildSignOff({
        id: `${id}-SO-1`,
        pointId: 'IP-E1',
        packageId: id,
        extent: chainageRange(from, asStation(from + random.int(200, 1000) * 1000)),
        signedOn: addDays(d('2025-02-01'), random.int(0, 400)),
        by: 'R Engineer',
      }),
    );

    if (random.bool(0.15)) {
      const start = random.int(0, 800) * 1000;
      ncrs.push(
        buildNcr({
          id: `${id}-NCR-1`,
          packageId: id,
          extent: chainageRange(asStation(from + start), asStation(from + start + 100_000)),
          raisedOn: addDays(d('2025-02-01'), random.int(0, 400)),
          description: 'layer thickness over the specification',
        }),
      );
    }
  }

  putAll(job.packages, packages);
  putAll(job.records, records);
  putAll(job.signOffs, signOffs);
  putAll(job.ncrs, ncrs);

  loadCommercial(job, packageCount, options, random);
  return job;
}

/**
 * The commercial side of a job this size.
 *
 * Kept in its own pass rather than woven into the package loop, because the
 * two are sized by different things. A road job has two hundred packages and
 * thirty subcontracts, and building the orders inside the package loop would
 * tie one number to the other and hide whichever is actually slow.
 */
function loadCommercial(
  job: Job,
  packageCount: number,
  options: BigJobOptions,
  random: Rng,
): void {
  const orders = options.subcontracts ?? 30;
  const returns = options.resourceReturns ?? 2000;

  job.settings = {
    ...job.settings,
    haulRate: moneyFromMajor(1, 'GBP'),
    outputAllowances: buildAllowances([
      { stepId: 'strip', metresPerHour: 10 },
      { stepId: 'excavate', metresPerHour: 12 },
    ]),
  };

  for (let i = 0; i < orders; i += 1) {
    const id = `SC-${String(i + 1).padStart(3, '0')}`;
    job.subcontracts.add(
      buildSubcontract({
        id,
        name: `earthworks package ${id}`,
        contractor: `contractor ${String(i + 1)}`,
        orderValue: moneyFromMajor(random.int(100_000, 900_000), 'GBP'),
        packageIds: [`WP-${String(random.int(1, packageCount)).padStart(4, '0')}`],
        placedOn: d('2024-12-02'),
      }),
    );

    let paid = 0;
    for (let period = 1; period <= 12; period += 1) {
      const gross = paid + random.int(5000, 40_000);
      const applicationId = `${id}-SA-${String(period).padStart(2, '0')}`;
      job.paymentApplications.add({
        id: applicationId,
        subcontractId: id,
        periodNumber: period,
        appliedOn: addDays(d('2025-01-25'), (period - 1) * 30),
        gross: moneyFromMajor(gross, 'GBP'),
        previouslyPaid: moneyFromMajor(Math.round(paid * 0.95), 'GBP'),
      });
      if (random.bool(0.7)) {
        job.paymentNotices.add({
          applicationId,
          servedOn: addDays(d('2025-01-25'), (period - 1) * 30 + random.int(28, 40)),
          valued: moneyFromMajor(Math.round(gross * 0.95), 'GBP'),
        });
      }
      paid = gross;
    }

    for (let n = 0; n < 3; n += 1) {
      const incurredOn = addDays(d('2025-01-05'), random.int(0, 300));
      job.contraCharges.add(
        buildContraCharge({
          id: `${id}-CC-${String(n)}`,
          subcontractId: id,
          reason: random.pick(['attendance', 'welfare', 'rectification', 'damage'] as const),
          description: 'a charge against the order',
          amount: moneyFromMajor(random.int(100, 8000), 'GBP'),
          incurredOn,
          ...(random.bool(0.6) ? { notifiedOn: addDays(incurredOn, random.int(0, 20)) } : {}),
        }),
      );
    }
  }

  for (let i = 0; i < returns; i += 1) {
    const packageId = `WP-${String(random.int(1, packageCount)).padStart(4, '0')}`;
    job.resourceReturns.add(
      buildResourceReturn({
        id: `RR-${String(i).padStart(5, '0')}`,
        packageId,
        stepId: random.pick(['strip', 'excavate', 'place'] as const),
        date: addDays(d('2025-01-06'), random.int(0, 400)),
        labour: [
          {
            resourceId: 'GANG-EARTH',
            description: 'earthworks gang',
            count: random.int(2, 8),
            hours: random.int(6, 12),
            kind: random.pick(['working', 'working', 'standing', 'idle'] as const),
          },
        ],
        plant: [
          {
            resourceId: 'PL-360',
            description: '22t excavator',
            count: 1,
            hours: random.int(4, 10),
            kind: 'working',
          },
        ],
        returnedBy: 'A Kerrigan',
      }),
    );
  }
}
