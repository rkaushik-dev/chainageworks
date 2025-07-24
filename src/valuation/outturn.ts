import { billAsAt } from '../domain/bill/revision.js';
import type { DayworkSchedule } from '../domain/daywork/schedule.js';
import type { DayworkSheet } from '../domain/daywork/sheet.js';
import { dayworkValue } from '../domain/daywork/sheet.js';
import type { CompletionTerms } from '../domain/delay/completion.js';
import type { DelayEvent } from '../domain/delay/event.js';
import { completionPosition, daysLate, liquidatedDamages } from '../domain/delay/completion.js';
import type { AdjustmentTerms } from '../domain/fluctuation/adjustment.js';
import type { PriceAdjustment } from '../domain/fluctuation/adjustment.js';
import { noAdjustment, priceAdjustment } from '../domain/fluctuation/adjustment.js';
import type { IndexSeries } from '../domain/fluctuation/index.js';
import type { Money } from '../domain/money/money.js';
import { addMoney, subtractMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { percentOf } from '../domain/money/percent.js';
import type { SiteCalendar } from '../domain/time/calendar.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { Variation } from '../domain/variation/variation.js';
import { variationValue } from '../domain/variation/apply.js';
import type { ProgressContext } from '../progress/context.js';
import type { FinalAccount, FinalAccountOptions } from './finalAccount.js';
import { buildFinalAccount } from './finalAccount.js';

/**
 * The anticipated final account: everything the job is going to be worth.
 *
 * Kept as separate lines rather than one number, because the five parts are
 * argued separately and by different people. Measured works are a surveyor's
 * argument, variations are a contract argument, damages are a programme
 * argument, and rolling them into a single outturn means the first person to
 * disagree with any of them disagrees with all of it.
 */
export interface Outturn {
  readonly asAt: SiteDate;
  readonly contractSum: Money;
  readonly account: FinalAccount;
  /** Forecast value of the measured works, from the bill and the trend. */
  readonly measuredWorks: Money;
  /** What the variations in force add to, or take off, the bill. */
  readonly variations: Money;
  readonly daywork: Money;
  readonly priceAdjustment: PriceAdjustment;
  /** Damages the employer would deduct at the forecast completion date. */
  readonly damages: Money;
  readonly total: Money;
  /** Total against the contract sum. Positive is over. */
  readonly movement: Money;
  readonly movementPercent: Percent;
}

export interface OutturnInput {
  readonly context: ProgressContext;
  readonly asAt: SiteDate;
  readonly contractSum: Money;
  readonly variations?: readonly Variation[];
  readonly dayworkSchedule?: DayworkSchedule | null;
  readonly dayworkSheets?: readonly DayworkSheet[];
  readonly adjustment?: AdjustmentTerms | null;
  readonly indices?: readonly IndexSeries[];
  readonly completion?: CompletionTerms | null;
  readonly delayEvents?: readonly DelayEvent[];
  readonly calendar?: SiteCalendar;
  readonly accountOptions?: FinalAccountOptions;
}

/**
 * Roll the whole account up at a date.
 *
 * Damages come off the total rather than sitting beside it, because that is
 * what actually happens to the money: the employer deducts them from the last
 * certificate. Reporting them alongside would let a job read as on budget
 * while it was sixty days late.
 */
export function buildOutturn(input: OutturnInput): Outturn {
  const currency = input.contractSum.currency;
  const account = buildFinalAccount(input.context, input.asAt, input.accountOptions ?? {});
  const bill = billAsAt(input.context.billHistory, input.asAt);

  const variations =
    input.variations === undefined || input.variations.length === 0
      ? zeroMoney(currency)
      : variationValue(bill, input.variations, input.asAt);

  const schedule = input.dayworkSchedule ?? null;
  const daywork =
    schedule === null
      ? zeroMoney(currency)
      : dayworkValue(schedule, input.dayworkSheets ?? [], input.asAt);

  const permanentWork = addMoney(addMoney(account.forecastOutturn, variations), daywork);
  const terms = input.adjustment ?? null;
  const adjustment =
    terms === null
      ? noAdjustment(permanentWork, input.asAt)
      : priceAdjustment(terms, input.indices ?? [], permanentWork, input.asAt);

  const damages = damagesAt(input, currency);
  const total = subtractMoney(addMoney(permanentWork, adjustment.amount), damages);

  return {
    asAt: input.asAt,
    contractSum: input.contractSum,
    account,
    measuredWorks: account.forecastOutturn,
    variations,
    daywork,
    priceAdjustment: adjustment,
    damages,
    total,
    movement: subtractMoney(total, input.contractSum),
    movementPercent: percentOf(subtractMoney(total, input.contractSum), input.contractSum),
  };
}

function damagesAt(input: OutturnInput, currency: Money['currency']): Money {
  const terms = input.completion ?? null;
  if (terms === null || input.calendar === undefined) return zeroMoney(currency);
  const position = completionPosition(
    terms,
    input.delayEvents ?? [],
    input.calendar,
    input.asAt,
  );
  return liquidatedDamages(terms, position, input.contractSum, input.asAt);
}

/** How late the job is forecast to be, in calendar days. */
export function forecastDaysLate(input: OutturnInput): number {
  const terms = input.completion ?? null;
  if (terms === null || input.calendar === undefined) return 0;
  const position = completionPosition(
    terms,
    input.delayEvents ?? [],
    input.calendar,
    input.asAt,
  );
  return daysLate(position, input.asAt);
}

/** The parts of the outturn, in the order a final account letter sets them out. */
export function outturnParts(outturn: Outturn): { name: string; amount: Money }[] {
  return [
    { name: 'measured works', amount: outturn.measuredWorks },
    { name: 'variations', amount: outturn.variations },
    { name: 'daywork', amount: outturn.daywork },
    { name: 'price adjustment', amount: outturn.priceAdjustment.amount },
    {
      name: 'liquidated damages',
      amount: { minor: -outturn.damages.minor, currency: outturn.damages.currency },
    },
  ];
}

export function isOverContractSum(outturn: Outturn): boolean {
  return outturn.movement.minor > 0;
}

/** The parts adding up, which is the check a reviewer does by hand. */
export function partsAddUp(outturn: Outturn): boolean {
  const parts = sumMoney(
    outturnParts(outturn).map((part) => part.amount),
    outturn.total.currency,
  );
  return parts.minor === outturn.total.minor;
}

export function describeOutturn(outturn: Outturn): string {
  const state = isOverContractSum(outturn) ? 'over' : 'under';
  return (
    `outturn at ${formatSiteDate(outturn.asAt)}, ` +
    `${String(Math.abs(outturn.movement.minor) / 100)} ${state} the contract sum`
  );
}
