import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { addMoney, sumMoney, zeroMoney } from '../money/money.js';
import type { SiteDate } from '../time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../time/siteDate.js';
import type { DayworkSchedule } from './schedule.js';
import { hoursValue, requireLabourRate, requirePlantRate, withAddition } from './schedule.js';

export interface LabourEntry {
  readonly rateId: string;
  readonly people: number;
  readonly hours: number;
  readonly overtimeHours: number;
}

export interface PlantEntry {
  readonly rateId: string;
  readonly hours: number;
  readonly overtimeHours: number;
}

export interface MaterialEntry {
  readonly description: string;
  readonly value: Money;
}

/**
 * A daywork sheet as it comes off site.
 *
 * The signature is the whole point. An unsigned sheet is a record of what the
 * contractor says happened, and it is worth nothing on a certificate: the
 * engineer has to have seen the gang on the day, because nobody can verify
 * three weeks later that four men were there for six hours.
 */
export interface DayworkSheet {
  readonly id: string;
  readonly reference: string;
  readonly packageId: string;
  /** The instruction this work was done under. */
  readonly instruction: string;
  readonly workedOn: SiteDate;
  readonly signedOn: SiteDate | null;
  readonly signedBy: string;
  readonly labour: readonly LabourEntry[];
  readonly plant: readonly PlantEntry[];
  readonly materials: readonly MaterialEntry[];
}

export interface DayworkSheetInput {
  readonly id: string;
  readonly reference: string;
  readonly packageId: string;
  readonly instruction: string;
  readonly workedOn: SiteDate;
  readonly signedOn?: SiteDate | null;
  readonly signedBy?: string;
  readonly labour?: readonly LabourEntry[];
  readonly plant?: readonly PlantEntry[];
  readonly materials?: readonly MaterialEntry[];
}

function checkHours(hours: number, what: string): void {
  if (!Number.isFinite(hours) || hours < 0) {
    throw new BadInputError(`${what} cannot be negative`, { hours });
  }
  if (hours > 24) {
    throw new BadInputError(`${what} cannot be more than a day`, { hours });
  }
}

export function buildDayworkSheet(input: DayworkSheetInput): DayworkSheet {
  const signedOn = input.signedOn ?? null;
  const signedBy = (input.signedBy ?? '').trim();
  if (signedOn !== null && signedBy === '') {
    throw new BadInputError('a signed daywork sheet has to name who signed it', {
      sheet: input.id,
    });
  }
  if (signedOn !== null && compareSiteDates(signedOn, input.workedOn) < 0) {
    throw new BadInputError('a daywork sheet cannot be signed before the work was done', {
      sheet: input.id,
      worked: formatSiteDate(input.workedOn),
      signed: formatSiteDate(signedOn),
    });
  }
  const labour = input.labour ?? [];
  const plant = input.plant ?? [];
  for (const entry of labour) {
    if (!Number.isInteger(entry.people) || entry.people < 1) {
      throw new BadInputError('a labour line needs at least one person', {
        sheet: input.id,
        rate: entry.rateId,
      });
    }
    checkHours(entry.hours, 'labour hours');
    checkHours(entry.overtimeHours, 'labour overtime hours');
  }
  for (const entry of plant) {
    checkHours(entry.hours, 'plant hours');
    checkHours(entry.overtimeHours, 'plant overtime hours');
  }
  if (labour.length === 0 && plant.length === 0 && (input.materials ?? []).length === 0) {
    throw new BadInputError('a daywork sheet with nothing on it is not a sheet', {
      sheet: input.id,
    });
  }
  return {
    id: input.id,
    reference: input.reference,
    packageId: input.packageId,
    instruction: input.instruction,
    workedOn: input.workedOn,
    signedOn,
    signedBy,
    labour: [...labour],
    plant: [...plant],
    materials: [...(input.materials ?? [])],
  };
}

/** Signed on or before the cut-off. A sheet signed later is next month's. */
export function isSignedAt(sheet: DayworkSheet, asAt: SiteDate): boolean {
  return sheet.signedOn !== null && compareSiteDates(sheet.signedOn, asAt) <= 0;
}

export interface DayworkValue {
  readonly labour: Money;
  readonly plant: Money;
  readonly materials: Money;
  readonly total: Money;
}

/**
 * Price one sheet.
 *
 * Each of the three heads is totalled, then its own percentage addition goes on
 * top, then the three are added. Applying one blended addition to the total
 * gives a different and larger answer, which is the mistake this ordering is
 * written to avoid.
 */
export function valueSheet(schedule: DayworkSchedule, sheet: DayworkSheet): DayworkValue {
  const currency = schedule.currency;

  const labour = sumMoney(
    sheet.labour.map((entry) => {
      const rate = requireLabourRate(schedule, entry.rateId);
      const one = hoursValue(rate, entry.hours, entry.overtimeHours, schedule.overtimeFactor);
      return sumMoney(new Array<Money>(entry.people).fill(one), currency);
    }),
    currency,
  );

  const plant = sumMoney(
    sheet.plant.map((entry) =>
      hoursValue(
        requirePlantRate(schedule, entry.rateId),
        entry.hours,
        entry.overtimeHours,
        schedule.overtimeFactor,
      ),
    ),
    currency,
  );

  const materials = sumMoney(
    sheet.materials.map((entry) => entry.value),
    currency,
  );

  const withLabour = withAddition(labour, schedule.additions.labour);
  const withPlant = withAddition(plant, schedule.additions.plant);
  const withMaterials = withAddition(materials, schedule.additions.materials);

  return {
    labour: withLabour,
    plant: withPlant,
    materials: withMaterials,
    total: addMoney(addMoney(withLabour, withPlant), withMaterials),
  };
}

/** Everything signed by a cut-off, priced and added. */
export function dayworkValue(
  schedule: DayworkSchedule,
  sheets: readonly DayworkSheet[],
  asAt: SiteDate,
): Money {
  const signed = sheets.filter((sheet) => isSignedAt(sheet, asAt));
  if (signed.length === 0) return zeroMoney(schedule.currency);
  return sumMoney(
    signed.map((sheet) => valueSheet(schedule, sheet).total),
    schedule.currency,
  );
}

/** Sheets that are still waiting on a signature, for the chase list. */
export function unsignedSheets(
  sheets: readonly DayworkSheet[],
  asAt: SiteDate,
): DayworkSheet[] {
  return sheets.filter(
    (sheet) => !isSignedAt(sheet, asAt) && compareSiteDates(sheet.workedOn, asAt) <= 0,
  );
}

export function describeSheet(sheet: DayworkSheet): string {
  const state =
    sheet.signedOn === null ? 'unsigned' : `signed ${formatSiteDate(sheet.signedOn)}`;
  return `${sheet.reference} worked ${formatSiteDate(sheet.workedOn)}, ${state}`;
}
