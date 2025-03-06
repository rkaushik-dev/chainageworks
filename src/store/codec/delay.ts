import type { CompletionTerms } from '../../domain/delay/completion.js';
import { buildCompletionTerms } from '../../domain/delay/completion.js';
import type { DelayCause, DelayEvent } from '../../domain/delay/event.js';
import { buildDelayEvent } from '../../domain/delay/event.js';
import { BadInputError } from '../../errors.js';
import {
  arrayFromJson,
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

const CAUSES: readonly DelayCause[] = [
  'weather',
  'variation',
  'access',
  'design',
  'utilities',
  'ground',
  'contractor',
];

function causeFromJson(value: unknown): DelayCause {
  const text = needString(value, 'delay cause');
  const found = CAUSES.find((c) => c === text);
  if (found === undefined) {
    throw new BadInputError('not a delay cause this tool knows', { cause: text });
  }
  return found;
}

export function completionToJson(terms: CompletionTerms): Record<string, unknown> {
  return {
    baseCompletion: dateToJson(terms.baseCompletion),
    damagesPerDay: moneyToJson(terms.damagesPerDay),
    damagesCap: terms.damagesCap === null ? null : percentToJson(terms.damagesCap),
    noticeDays: terms.noticeDays,
  };
}

export function completionFromJson(value: unknown): CompletionTerms | null {
  if (value === null || value === undefined) return null;
  const raw = objectFromJson(value, 'completion terms');
  const cap = raw['damagesCap'];
  return buildCompletionTerms({
    baseCompletion: dateFromJson(raw['baseCompletion']),
    damagesPerDay: moneyFromJson(raw['damagesPerDay']),
    damagesCap: cap === null || cap === undefined ? null : percentFromJson(cap),
    noticeDays: Number(raw['noticeDays']),
  });
}

export function delayEventToJson(event: DelayEvent): Record<string, unknown> {
  return {
    id: event.id,
    reference: event.reference,
    cause: event.cause,
    description: event.description,
    from: dateToJson(event.period.from),
    to: dateToJson(event.period.to),
    extent: event.extent === null ? null : rangeToJson(event.extent),
    packageIds: [...event.packageIds],
    notifiedOn: optionalDateToJson(event.notifiedOn),
    awardedDays: event.awardedDays,
  };
}

export function delayEventFromJson(value: unknown): DelayEvent {
  const raw = objectFromJson(value, 'delay event');
  const extent = raw['extent'];
  const awarded = raw['awardedDays'];
  return buildDelayEvent({
    id: needString(raw['id'], 'event id'),
    reference: optionalString(raw['reference']),
    cause: causeFromJson(raw['cause']),
    description: needString(raw['description'], 'description'),
    from: dateFromJson(raw['from']),
    to: dateFromJson(raw['to']),
    extent: extent === null || extent === undefined ? null : rangeFromJson(extent),
    packageIds: arrayFromJson(raw['packageIds'] ?? [], 'package ids').map((id) =>
      needString(id, 'package id'),
    ),
    notifiedOn: optionalDateFromJson(raw['notifiedOn']),
    awardedDays: awarded === null || awarded === undefined ? null : Number(awarded),
  });
}
