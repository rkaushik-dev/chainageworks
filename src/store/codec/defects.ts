import { BadInputError } from '../../errors.js';
import type { Attribution, Defect, DefectSeverity } from '../../domain/defects/defect.js';
import { buildDefect } from '../../domain/defects/defect.js';
import {
  dateFromJson,
  dateToJson,
  needString,
  objectFromJson,
  optionalDateFromJson,
  optionalDateToJson,
  optionalString,
  rangeFromJson,
  rangeToJson,
} from './values.js';

const SEVERITIES: readonly DefectSeverity[] = ['minor', 'major', 'safety'];
const ATTRIBUTIONS: readonly Attribution[] = ['contractor', 'employer', 'unresolved'];

function oneOf<T extends string>(value: unknown, allowed: readonly T[], what: string): T {
  const text = needString(value, what);
  const found = allowed.find((v) => v === text);
  if (found === undefined) {
    throw new BadInputError(`not a ${what} this tool knows`, { got: text });
  }
  return found;
}

export function defectToJson(defect: Defect): Record<string, unknown> {
  return {
    id: defect.id,
    reference: defect.reference,
    packageId: defect.packageId,
    extent: defect.extent === null ? null : rangeToJson(defect.extent),
    description: defect.description,
    severity: defect.severity,
    attribution: defect.attribution,
    raisedOn: dateToJson(defect.raisedOn),
    dueBy: dateToJson(defect.dueBy),
    rectifiedOn: optionalDateToJson(defect.rectifiedOn),
  };
}

export function defectFromJson(value: unknown): Defect {
  const raw = objectFromJson(value, 'defect');
  const extent = raw['extent'];
  return buildDefect({
    id: needString(raw['id'], 'defect id'),
    reference: optionalString(raw['reference']),
    packageId: needString(raw['packageId'], 'package id'),
    extent: extent === null || extent === undefined ? null : rangeFromJson(extent),
    description: needString(raw['description'], 'description'),
    severity: oneOf(raw['severity'] ?? 'minor', SEVERITIES, 'defect severity'),
    attribution: oneOf(raw['attribution'] ?? 'contractor', ATTRIBUTIONS, 'defect attribution'),
    raisedOn: dateFromJson(raw['raisedOn']),
    dueBy: dateFromJson(raw['dueBy']),
    rectifiedOn: optionalDateFromJson(raw['rectifiedOn']),
  });
}
