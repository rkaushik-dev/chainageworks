import { parseItemCode } from '../../src/domain/bill/itemCode.js';
import { chainageRange } from '../../src/domain/chainage/range.js';
import { parseStation } from '../../src/domain/chainage/station.js';
import type { MeasurementMethod, SurveyRecord } from '../../src/domain/survey/record.js';
import { buildSurveyRecord } from '../../src/domain/survey/record.js';
import { quantity } from '../../src/domain/quantity/quantity.js';
import type { UnitCode } from '../../src/domain/quantity/unit.js';
import { parseSiteDate } from '../../src/domain/time/siteDate.js';

export interface RecordSpec {
  readonly id: string;
  readonly packageId?: string;
  readonly code: string;
  readonly stepId: string;
  readonly from: string;
  readonly to: string;
  readonly amount: number;
  readonly unit: UnitCode;
  readonly on: string;
  readonly by?: string;
  readonly method?: MeasurementMethod;
  readonly supersedes?: string;
}

export function record(spec: RecordSpec): SurveyRecord {
  return buildSurveyRecord({
    id: spec.id,
    packageId: spec.packageId ?? 'WP-01',
    code: parseItemCode(spec.code),
    stepId: spec.stepId,
    extent: chainageRange(parseStation(spec.from), parseStation(spec.to)),
    quantity: quantity(spec.amount, spec.unit),
    measuredOn: parseSiteDate(spec.on),
    bookedBy: spec.by ?? 'J Foreman',
    ...(spec.method === undefined ? {} : { method: spec.method }),
    ...(spec.supersedes === undefined ? {} : { supersedes: spec.supersedes }),
  });
}

export function records(specs: readonly RecordSpec[]): SurveyRecord[] {
  return specs.map(record);
}
