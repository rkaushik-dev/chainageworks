import { BadInputError } from '../../errors.js';
import type { HourKind, ResourceLine, ResourceReturn } from '../../domain/resource/return.js';
import { buildResourceReturn, HOUR_KINDS } from '../../domain/resource/return.js';
import type { OutputAllowance } from '../../progress/productivity.js';
import {
  arrayFromJson,
  dateFromJson,
  dateToJson,
  needString,
  objectFromJson,
  optionalString,
} from './values.js';

function hourKindFromJson(value: unknown): HourKind {
  const text = needString(value ?? 'working', 'hour kind');
  const found = HOUR_KINDS.find((kind) => kind === text);
  if (found === undefined) {
    throw new BadInputError('not a way of spending an hour this tool knows', { got: text });
  }
  return found;
}

function lineToJson(line: ResourceLine): Record<string, unknown> {
  return {
    resourceId: line.resourceId,
    description: line.description,
    count: line.count,
    hours: line.hours,
    kind: line.kind,
  };
}

function lineFromJson(value: unknown): ResourceLine {
  const raw = objectFromJson(value, 'resource line');
  return {
    resourceId: needString(raw['resourceId'], 'resource id'),
    description: optionalString(raw['description']),
    count: Number(raw['count']),
    hours: Number(raw['hours']),
    kind: hourKindFromJson(raw['kind']),
  };
}

export function resourceReturnToJson(entry: ResourceReturn): Record<string, unknown> {
  return {
    id: entry.id,
    packageId: entry.packageId,
    stepId: entry.stepId,
    date: dateToJson(entry.date),
    shift: entry.shift,
    labour: entry.labour.map(lineToJson),
    plant: entry.plant.map(lineToJson),
    returnedBy: entry.returnedBy,
    note: entry.note,
  };
}

export function resourceReturnFromJson(value: unknown): ResourceReturn {
  const raw = objectFromJson(value, 'resource return');
  const stepId = raw['stepId'];
  return buildResourceReturn({
    id: needString(raw['id'], 'return id'),
    packageId: needString(raw['packageId'], 'package id'),
    stepId: stepId === null || stepId === undefined ? null : needString(stepId, 'step id'),
    date: dateFromJson(raw['date']),
    shift: raw['shift'] === 'night' ? 'night' : 'day',
    labour: arrayFromJson(raw['labour'] ?? [], 'labour lines').map(lineFromJson),
    plant: arrayFromJson(raw['plant'] ?? [], 'plant lines').map(lineFromJson),
    returnedBy: needString(raw['returnedBy'], 'returned by'),
    note: optionalString(raw['note']),
  });
}

export function allowanceToJson(allowance: OutputAllowance): Record<string, unknown> {
  return { stepId: allowance.stepId, metresPerHour: allowance.metresPerHour };
}

export function allowanceFromJson(value: unknown): OutputAllowance {
  const raw = objectFromJson(value, 'output allowance');
  return {
    stepId: needString(raw['stepId'], 'step id'),
    metresPerHour: Number(raw['metresPerHour']),
  };
}
