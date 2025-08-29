import { BadInputError } from '../../errors.js';
import type { EarlyWarning, RiskOwner, RiskState } from '../../domain/risk/earlyWarning.js';
import { buildEarlyWarning } from '../../domain/risk/earlyWarning.js';
import {
  dateFromJson,
  dateToJson,
  moneyFromJson,
  moneyToJson,
  needString,
  objectFromJson,
  optionalDateFromJson,
  optionalDateToJson,
  optionalString,
  percentFromJson,
  percentToJson,
  rangeFromJson,
  rangeToJson,
} from './values.js';

const OWNERS: readonly RiskOwner[] = ['contractor', 'employer', 'shared', 'unresolved'];
const STATES: readonly RiskState[] = ['open', 'mitigated', 'happened', 'avoided'];

function oneOf<T extends string>(value: unknown, allowed: readonly T[], what: string): T {
  const text = needString(value, what);
  const found = allowed.find((v) => v === text);
  if (found === undefined) {
    throw new BadInputError(`not a ${what} this tool knows`, { got: text });
  }
  return found;
}

export function earlyWarningToJson(warning: EarlyWarning): Record<string, unknown> {
  return {
    id: warning.id,
    reference: warning.reference,
    description: warning.description,
    owner: warning.owner,
    state: warning.state,
    extent: warning.extent === null ? null : rangeToJson(warning.extent),
    raisedOn: dateToJson(warning.raisedOn),
    meetingOn: optionalDateToJson(warning.meetingOn),
    closedOn: optionalDateToJson(warning.closedOn),
    costIfItHappens: moneyToJson(warning.costIfItHappens),
    likelihood: percentToJson(warning.likelihood),
    delayDays: warning.delayDays,
  };
}

export function earlyWarningFromJson(value: unknown): EarlyWarning {
  const raw = objectFromJson(value, 'early warning');
  const extent = raw['extent'];
  return buildEarlyWarning({
    id: needString(raw['id'], 'warning id'),
    reference: optionalString(raw['reference']),
    description: needString(raw['description'], 'description'),
    owner: oneOf(raw['owner'] ?? 'unresolved', OWNERS, 'risk owner'),
    state: oneOf(raw['state'] ?? 'open', STATES, 'risk state'),
    extent: extent === null || extent === undefined ? null : rangeFromJson(extent),
    raisedOn: dateFromJson(raw['raisedOn']),
    meetingOn: optionalDateFromJson(raw['meetingOn']),
    closedOn: optionalDateFromJson(raw['closedOn']),
    costIfItHappens: moneyFromJson(raw['costIfItHappens']),
    likelihood: percentFromJson(raw['likelihood']),
    delayDays: Number(raw['delayDays'] ?? 0),
  });
}
