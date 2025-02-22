import type { DayworkSchedule } from '../../domain/daywork/schedule.js';
import { buildDayworkSchedule } from '../../domain/daywork/schedule.js';
import type { DayworkSheet } from '../../domain/daywork/sheet.js';
import { buildDayworkSheet } from '../../domain/daywork/sheet.js';
import {
  arrayFromJson,
  currencyFromJson,
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
  rateFromJson,
  rateToJson,
} from './values.js';

/**
 * The daywork half of the job document, kept in its own file.
 *
 * A sheet has three kinds of line and a schedule has two groups of rates, which
 * is enough shape that folding it into the main codec made that file hard to
 * read for anything else.
 */

function ratesToJson(group: DayworkSchedule['labour']): Record<string, unknown>[] {
  return group.map((entry) => ({
    id: entry.id,
    description: entry.description,
    rate: rateToJson(entry.rate),
  }));
}

function ratesFromJson(value: unknown, what: string): DayworkSchedule['labour'] {
  return arrayFromJson(value, what).map((entry) => {
    const raw = objectFromJson(entry, 'daywork rate');
    return {
      id: needString(raw['id'], 'rate id'),
      description: optionalString(raw['description']),
      rate: rateFromJson(raw['rate']),
    };
  });
}

export function dayworkScheduleToJson(schedule: DayworkSchedule): Record<string, unknown> {
  return {
    currency: schedule.currency,
    labour: ratesToJson(schedule.labour),
    plant: ratesToJson(schedule.plant),
    additions: {
      labour: percentToJson(schedule.additions.labour),
      plant: percentToJson(schedule.additions.plant),
      materials: percentToJson(schedule.additions.materials),
    },
    overtimeFactor: schedule.overtimeFactor,
  };
}

export function dayworkScheduleFromJson(value: unknown): DayworkSchedule | null {
  if (value === null || value === undefined) return null;
  const raw = objectFromJson(value, 'daywork schedule');
  const additions = objectFromJson(raw['additions'], 'daywork additions');
  return buildDayworkSchedule({
    currency: currencyFromJson(raw['currency']),
    labour: ratesFromJson(raw['labour'], 'daywork labour rates'),
    plant: ratesFromJson(raw['plant'], 'daywork plant rates'),
    additions: {
      labour: percentFromJson(additions['labour']),
      plant: percentFromJson(additions['plant']),
      materials: percentFromJson(additions['materials']),
    },
    overtimeFactor: Number(raw['overtimeFactor']),
  });
}

export function dayworkSheetToJson(sheet: DayworkSheet): Record<string, unknown> {
  return {
    id: sheet.id,
    reference: sheet.reference,
    packageId: sheet.packageId,
    instruction: sheet.instruction,
    workedOn: dateToJson(sheet.workedOn),
    signedOn: optionalDateToJson(sheet.signedOn),
    signedBy: sheet.signedBy,
    labour: sheet.labour.map((entry) => ({
      rateId: entry.rateId,
      people: entry.people,
      hours: entry.hours,
      overtimeHours: entry.overtimeHours,
    })),
    plant: sheet.plant.map((entry) => ({
      rateId: entry.rateId,
      hours: entry.hours,
      overtimeHours: entry.overtimeHours,
    })),
    materials: sheet.materials.map((entry) => ({
      description: entry.description,
      value: moneyToJson(entry.value),
    })),
  };
}

export function dayworkSheetFromJson(value: unknown): DayworkSheet {
  const raw = objectFromJson(value, 'daywork sheet');
  return buildDayworkSheet({
    id: needString(raw['id'], 'sheet id'),
    reference: optionalString(raw['reference']),
    packageId: needString(raw['packageId'], 'package id'),
    instruction: optionalString(raw['instruction']),
    workedOn: dateFromJson(raw['workedOn']),
    signedOn: optionalDateFromJson(raw['signedOn']),
    signedBy: optionalString(raw['signedBy']),
    labour: arrayFromJson(raw['labour'] ?? [], 'labour lines').map((entry) => {
      const line = objectFromJson(entry, 'labour line');
      return {
        rateId: needString(line['rateId'], 'rate id'),
        people: Number(line['people']),
        hours: Number(line['hours']),
        overtimeHours: Number(line['overtimeHours'] ?? 0),
      };
    }),
    plant: arrayFromJson(raw['plant'] ?? [], 'plant lines').map((entry) => {
      const line = objectFromJson(entry, 'plant line');
      return {
        rateId: needString(line['rateId'], 'rate id'),
        hours: Number(line['hours']),
        overtimeHours: Number(line['overtimeHours'] ?? 0),
      };
    }),
    materials: arrayFromJson(raw['materials'] ?? [], 'material lines').map((entry) => {
      const line = objectFromJson(entry, 'material line');
      return {
        description: optionalString(line['description']),
        value: moneyFromJson(line['value']),
      };
    }),
  });
}
