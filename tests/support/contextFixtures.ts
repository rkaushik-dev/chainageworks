import { buildBillHistory } from '../../src/domain/bill/revision.js';
import type { BillHistory } from '../../src/domain/bill/revision.js';
import { buildProfileRegistry } from '../../src/domain/credit/catalogue.js';
import { BULK_EARTHWORKS, PAVEMENT } from '../../src/domain/credit/catalogue.js';
import { chainageRange } from '../../src/domain/chainage/range.js';
import { parseStation } from '../../src/domain/chainage/station.js';
import type { Ncr } from '../../src/domain/itp/ncr.js';
import { buildNcr } from '../../src/domain/itp/ncr.js';
import type { InspectionPlan } from '../../src/domain/itp/plan.js';
import { buildInspectionPlan } from '../../src/domain/itp/plan.js';
import type { SignOff, SignOffOutcome } from '../../src/domain/itp/signOff.js';
import { buildSignOff } from '../../src/domain/itp/signOff.js';
import { buildRegister } from '../../src/domain/packages/register.js';
import type { SurveyRecord } from '../../src/domain/survey/record.js';
import { buildCalendar } from '../../src/domain/time/calendar.js';
import { parseSiteDate } from '../../src/domain/time/siteDate.js';
import type { ProgressContext } from '../../src/progress/context.js';
import { buildProgressContext } from '../../src/progress/context.js';
import { sampleBill } from './billFixtures.js';
import { samplePackages } from './packageFixtures.js';
import { records } from './surveyFixtures.js';

export const d = parseSiteDate;
export const at = parseStation;
export const range = (from: string, to: string) => chainageRange(at(from), at(to));

export const earthworksPlan: InspectionPlan = buildInspectionPlan(
  {
    id: 'ITP-EW',
    name: 'earthworks inspection and test plan',
    profileId: 'bulk-earthworks',
    points: [
      {
        id: 'IP-E1',
        name: 'formation level and tolerance',
        type: 'hold',
        stepId: 'excavate',
        discipline: 'geotech',
      },
      {
        id: 'IP-E2',
        name: 'layer compaction density',
        type: 'hold',
        stepId: 'place',
        discipline: 'geotech',
      },
      { id: 'IP-E3', name: 'topsoil stockpile location', type: 'witness', stepId: 'strip' },
    ],
  },
  BULK_EARTHWORKS,
);

export const pavementPlan: InspectionPlan = buildInspectionPlan(
  {
    id: 'ITP-PAV',
    name: 'pavement inspection and test plan',
    profileId: 'pavement',
    points: [{ id: 'IP-P1', name: 'sub base level', type: 'hold', stepId: 'subbase' }],
  },
  PAVEMENT,
);

export const sampleHistory = (): BillHistory =>
  buildBillHistory([
    {
      reference: 'Rev A',
      effectiveFrom: d('2025-01-01'),
      bill: sampleBill(),
      note: 'contract bill at award',
    },
  ]);

export function signOff(
  id: string,
  pointId: string,
  packageId: string,
  from: string,
  to: string,
  on: string,
  outcome?: SignOffOutcome,
): SignOff {
  return buildSignOff({
    id,
    pointId,
    packageId,
    extent: range(from, to),
    signedOn: d(on),
    by: 'R Engineer',
    ...(outcome === undefined ? {} : { outcome }),
  });
}

export const sampleRecords = (): SurveyRecord[] =>
  records([
    {
      id: 'SR-01',
      packageId: 'WP-01',
      code: 'E423.1',
      stepId: 'strip',
      from: '12+000',
      to: '12+800',
      amount: 640,
      unit: 'm3',
      on: '2025-03-03',
    },
    {
      id: 'SR-02',
      packageId: 'WP-01',
      code: 'E423.1',
      stepId: 'excavate',
      from: '12+000',
      to: '12+600',
      amount: 5200,
      unit: 'm3',
      on: '2025-03-10',
    },
    {
      id: 'SR-03',
      packageId: 'WP-01',
      code: 'E532.1',
      stepId: 'place',
      from: '12+000',
      to: '12+400',
      amount: 2400,
      unit: 'm3',
      on: '2025-03-20',
    },
  ]);

export const sampleSignOffs = (): SignOff[] => [
  signOff('SO-01', 'IP-E1', 'WP-01', '12+000', '12+400', '2025-03-12'),
  signOff('SO-02', 'IP-E2', 'WP-01', '12+000', '12+200', '2025-03-22'),
];

export const sampleNcrs = (): Ncr[] => [
  buildNcr({
    id: 'NCR-01',
    packageId: 'WP-01',
    extent: range('12+100', '12+150'),
    stepId: 'place',
    raisedOn: d('2025-03-25'),
    description: 'layer thickness over the specified 250 mm',
  }),
];

export interface ContextOverrides {
  readonly records?: readonly SurveyRecord[];
  readonly signOffs?: readonly SignOff[];
  readonly ncrs?: readonly Ncr[];
  readonly plans?: readonly InspectionPlan[];
}

export function sampleContext(overrides: ContextOverrides = {}): ProgressContext {
  return buildProgressContext({
    billHistory: sampleHistory(),
    register: buildRegister(samplePackages()),
    profiles: buildProfileRegistry(),
    plans: overrides.plans ?? [earthworksPlan, pavementPlan],
    signOffs: overrides.signOffs ?? sampleSignOffs(),
    ncrs: overrides.ncrs ?? sampleNcrs(),
    records: overrides.records ?? sampleRecords(),
    calendar: buildCalendar({ id: 'C1', name: 'main works' }),
  });
}
