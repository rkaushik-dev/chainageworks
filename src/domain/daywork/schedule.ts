import { BadInputError, NotFoundError } from '../../errors.js';
import type { CurrencyCode, Money } from '../money/money.js';
import { addMoney, scaleMoney, zeroMoney } from '../money/money.js';
import type { Percent } from '../money/percent.js';
import { applyPercent, percent } from '../money/percent.js';
import type { Rate } from '../money/rate.js';
import { extend, rate } from '../money/rate.js';
import { quantity } from '../quantity/quantity.js';

/**
 * The daywork schedule the contract was tendered with.
 *
 * Daywork is the last resort: it is what work is paid at when it cannot be
 * measured, and it is priced from a schedule agreed at tender rather than from
 * the bill. The percentage additions are the part that gets argued over, so
 * they are held separately from the rates and applied once, at the end.
 */
export interface DayworkRate {
  readonly id: string;
  readonly description: string;
  readonly rate: Rate;
}

export interface DayworkAdditions {
  readonly labour: Percent;
  readonly plant: Percent;
  readonly materials: Percent;
}

export interface DayworkSchedule {
  readonly currency: CurrencyCode;
  readonly labour: readonly DayworkRate[];
  readonly plant: readonly DayworkRate[];
  readonly additions: DayworkAdditions;
  /** What an hour outside normal working hours is worth, as a multiplier. */
  readonly overtimeFactor: number;
}

export interface DayworkScheduleInput {
  readonly currency?: CurrencyCode;
  readonly labour: readonly DayworkRate[];
  readonly plant: readonly DayworkRate[];
  readonly additions?: Partial<DayworkAdditions>;
  readonly overtimeFactor?: number;
}

export const DEFAULT_ADDITIONS: DayworkAdditions = {
  labour: percent(88),
  plant: percent(12.5),
  materials: percent(12.5),
};

export function dayworkRate(
  id: string,
  description: string,
  perHour: number,
  currency: CurrencyCode = 'GBP',
): DayworkRate {
  return { id, description, rate: rate(perHour, currency, 'hr') };
}

export function buildDayworkSchedule(input: DayworkScheduleInput): DayworkSchedule {
  const currency = input.currency ?? 'GBP';
  const overtimeFactor = input.overtimeFactor ?? 1.5;
  if (!Number.isFinite(overtimeFactor) || overtimeFactor < 1) {
    throw new BadInputError('an overtime factor is at least one', { overtimeFactor });
  }
  for (const group of [input.labour, input.plant]) {
    const seen = new Set<string>();
    for (const entry of group) {
      if (seen.has(entry.id)) {
        throw new BadInputError('two daywork rates share an id', { rate: entry.id });
      }
      seen.add(entry.id);
      if (entry.rate.currency !== currency) {
        throw new BadInputError('a daywork rate is in another currency', {
          rate: entry.id,
          currency: entry.rate.currency,
        });
      }
      if (entry.rate.milliMinor < 0) {
        throw new BadInputError('a daywork rate cannot be negative', { rate: entry.id });
      }
    }
  }
  return {
    currency,
    labour: [...input.labour],
    plant: [...input.plant],
    additions: { ...DEFAULT_ADDITIONS, ...input.additions },
    overtimeFactor,
  };
}

export function findLabourRate(schedule: DayworkSchedule, id: string): DayworkRate | null {
  return schedule.labour.find((r) => r.id === id) ?? null;
}

export function requireLabourRate(schedule: DayworkSchedule, id: string): DayworkRate {
  const found = findLabourRate(schedule, id);
  if (found === null) throw new NotFoundError('daywork labour rate', id);
  return found;
}

export function findPlantRate(schedule: DayworkSchedule, id: string): DayworkRate | null {
  return schedule.plant.find((r) => r.id === id) ?? null;
}

export function requirePlantRate(schedule: DayworkSchedule, id: string): DayworkRate {
  const found = findPlantRate(schedule, id);
  if (found === null) throw new NotFoundError('daywork plant rate', id);
  return found;
}

/**
 * Hours at a rate, with overtime charged at the contract factor.
 *
 * The factor multiplies the rate and not the addition, which is what the
 * schedule says: the overheads on an hour do not go up because the hour was
 * worked in the evening.
 */
export function hoursValue(
  entry: DayworkRate,
  hours: number,
  overtimeHours: number,
  overtimeFactor: number,
): Money {
  const normal = extend(entry.rate, quantity(hours, 'hr'));
  const overtime = extend(entry.rate, quantity(overtimeHours, 'hr'));
  return addMoney(normal, scaleMoney(overtime, overtimeFactor));
}

export function withAddition(value: Money, addition: Percent): Money {
  return addMoney(value, applyPercent(value, addition));
}

export function zeroFor(schedule: DayworkSchedule): Money {
  return zeroMoney(schedule.currency);
}

export function describeSchedule(schedule: DayworkSchedule): string {
  return (
    `${String(schedule.labour.length)} labour rates, ` +
    `${String(schedule.plant.length)} plant rates, ` +
    `overtime at ${schedule.overtimeFactor.toFixed(2)}`
  );
}
