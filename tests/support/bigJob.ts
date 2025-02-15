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
import { buildSurveyRecord } from '../../src/domain/survey/record.js';
import { addDays } from '../../src/domain/time/siteDate.js';
import { createJob } from '../../src/store/job.js';
import type { Job } from '../../src/store/job.js';
import { putAll } from '../../src/store/repository.js';
import { d } from './contextFixtures.js';
import { rng } from './random.js';

export interface BigJobOptions {
  /** Packages down the alignment. Each is a kilometre. */
  readonly packages?: number;
  /** Survey records booked per package. */
  readonly recordsPerPackage?: number;
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
  return job;
}
