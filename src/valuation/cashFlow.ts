import { BadInputError } from '../errors.js';
import type { CurrencyCode, Money } from '../domain/money/money.js';
import { addMoney, subtractMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { compareSiteDates, formatSiteDate } from '../domain/time/siteDate.js';
import type { ValuationPeriod } from './period.js';

/**
 * One month of the cash forecast.
 *
 * Certified and received are held on different dates on purpose. The
 * certificate is a fact about the works and the receipt is a fact about the
 * bank, and the gap between them is the one that decides whether the job can
 * pay its subcontractors. Reporting them on the same date, which is what a
 * valuation report does, makes that gap invisible.
 */
export interface CashPoint {
  readonly period: number;
  readonly cutOff: SiteDate;
  readonly dueOn: SiteDate;
  /** Net certified to the end of this period, cumulative. */
  readonly certified: Money;
  /** The movement this period, which is what the certificate is worth. */
  readonly certificate: Money;
  /** Cash actually expected in the bank on the due date. */
  readonly receipt: Money;
  /** Cumulative cash in by the due date. */
  readonly received: Money;
  /** Certified and not yet received. */
  readonly outstanding: Money;
}

export interface CashFlowInput {
  readonly periods: readonly ValuationPeriod[];
  /** Net certified to date at the end of each period, in period order. */
  readonly certified: readonly Money[];
  readonly currency: CurrencyCode;
  /** Certificates paid this many periods after they are due. Zero is on time. */
  readonly latePeriods?: number;
}

/**
 * Turn a certified series into a forecast of cash in.
 *
 * Late payment is modelled as a whole number of periods rather than a number
 * of days, because that is how it actually behaves on a monthly certificate: a
 * payer who misses the due date does not pay four days late, it pays with the
 * next run. Modelling it in days produces a forecast that is precisely wrong.
 */
export function buildCashFlow(input: CashFlowInput): CashPoint[] {
  const late = input.latePeriods ?? 0;
  if (!Number.isInteger(late) || late < 0) {
    throw new BadInputError('late payment is a whole number of periods', { late });
  }
  if (input.certified.length !== input.periods.length) {
    throw new BadInputError('the certified series does not match the schedule', {
      certified: input.certified.length,
      periods: input.periods.length,
    });
  }

  const zero = zeroMoney(input.currency);
  const points: CashPoint[] = [];

  for (const [index, period] of input.periods.entries()) {
    const certified = input.certified[index] ?? zero;
    const previous = index === 0 ? zero : (input.certified[index - 1] ?? zero);
    const paidIndex = index - late;
    const received = paidIndex < 0 ? zero : (input.certified[paidIndex] ?? zero);
    const receivedBefore = paidIndex <= 0 ? zero : (input.certified[paidIndex - 1] ?? zero);

    points.push({
      period: period.number,
      cutOff: period.cutOff,
      dueOn: period.dueOn,
      certified,
      certificate: subtractMoney(certified, previous),
      receipt: subtractMoney(received, receivedBefore),
      received,
      outstanding: subtractMoney(certified, received),
    });
  }
  return points;
}

/** The most the job is ever owed at once, and the period it happens in. */
export function peakExposure(points: readonly CashPoint[]): CashPoint | null {
  if (points.length === 0) return null;
  return points.reduce((worst, point) =>
    point.outstanding.minor > worst.outstanding.minor ? point : worst,
  );
}

export function totalReceipts(points: readonly CashPoint[], currency: CurrencyCode): Money {
  return sumMoney(
    points.map((p) => p.receipt),
    currency,
  );
}

/** Cash expected in over a window, which is what a funding request asks for. */
export function receiptsBetween(
  points: readonly CashPoint[],
  from: SiteDate,
  to: SiteDate,
  currency: CurrencyCode,
): Money {
  return points
    .filter(
      (point) =>
        compareSiteDates(point.dueOn, from) >= 0 && compareSiteDates(point.dueOn, to) <= 0,
    )
    .reduce((total, point) => addMoney(total, point.receipt), zeroMoney(currency));
}

/** The first period where the forecast says nothing new comes in. */
export function firstDryPeriod(points: readonly CashPoint[]): number | null {
  const found = points.find((point) => point.receipt.minor === 0 && point.period > 1);
  return found?.period ?? null;
}

export function describeCashPoint(point: CashPoint): string {
  return (
    `period ${String(point.period)}, due ${formatSiteDate(point.dueOn)}, ` +
    `${String(point.outstanding.minor / 100)} outstanding`
  );
}
