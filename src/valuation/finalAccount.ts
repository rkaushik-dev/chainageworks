import { billAsAt } from '../domain/bill/revision.js';
import type { BillItem } from '../domain/bill/billItem.js';
import { billedValue, isEarnable } from '../domain/bill/billItem.js';
import type { ItemCode } from '../domain/bill/itemCode.js';
import { formatItemCode, itemCodeKey } from '../domain/bill/itemCode.js';
import type { Money } from '../domain/money/money.js';
import { maxMoney, subtractMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import { percent, percentFraction } from '../domain/money/percent.js';
import { extend } from '../domain/money/rate.js';
import { measuredQuantity } from '../domain/survey/record.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from '../progress/context.js';
import { earnAll } from '../progress/earn.js';

/**
 * How far through an item has to be before its trend is worth extrapolating.
 *
 * Below this the sample is too small: an item three percent complete whose
 * first measure ran long would forecast at four times the bill, and a forecast
 * that swings like that is worse than no forecast at all.
 */
export const DEFAULT_TREND_THRESHOLD: Percent = percent(25);

export interface FinalAccountLine {
  readonly code: ItemCode;
  readonly description: string;
  readonly billed: Money;
  /** What has actually been measured, at the rate in force. */
  readonly measured: Money;
  readonly percentComplete: number;
  readonly forecast: Money;
  readonly movement: Money;
  /** True when the forecast came from the trend rather than from the bill. */
  readonly extrapolated: boolean;
}

export interface FinalAccount {
  readonly asAt: SiteDate;
  readonly billTotal: Money;
  readonly measuredToDate: Money;
  readonly forecastOutturn: Money;
  readonly movement: Money;
  readonly lines: readonly FinalAccountLine[];
}

export interface FinalAccountOptions {
  readonly trendThreshold?: Percent;
}

/**
 * The forecast outturn: what the job is going to cost if it carries on the way
 * it has been going.
 *
 * Two answers are combined, item by item. An item that has barely started is
 * forecast at its billed value, because the bill is the best information there
 * is. An item that is well under way is forecast from its own trend, because by
 * then the ground has already told everyone what the bill got wrong. The
 * threshold between the two is the whole judgement, so it is a parameter and
 * the report says which side each line fell.
 */
export function buildFinalAccount(
  context: ProgressContext,
  asAt: SiteDate,
  options: FinalAccountOptions = {},
): FinalAccount {
  const bill = billAsAt(context.billHistory, asAt);
  const threshold = options.trendThreshold ?? DEFAULT_TREND_THRESHOLD;
  const currency = bill.currency;
  const fractions = packageFractions(context, asAt);

  const lines = bill.items.map((item) =>
    lineFor(context, item, asAt, fractions, threshold, currency),
  );

  const billTotal = sumMoney(
    lines.map((l) => l.billed),
    currency,
  );
  const forecastOutturn = sumMoney(
    lines.map((l) => l.forecast),
    currency,
  );
  return {
    asAt,
    billTotal,
    measuredToDate: sumMoney(
      lines.map((l) => l.measured),
      currency,
    ),
    forecastOutturn,
    movement: subtractMoney(forecastOutturn, billTotal),
    lines,
  };
}

/** How far each package has got, so an item can be weighted across them. */
function packageFractions(context: ProgressContext, asAt: SiteDate): Map<string, number> {
  const out = new Map<string, number>();
  for (const earning of earnAll(context, asAt)) {
    out.set(earning.packageId, percentFraction(earning.fraction));
  }
  return out;
}

function lineFor(
  context: ProgressContext,
  item: BillItem,
  asAt: SiteDate,
  fractions: Map<string, number>,
  threshold: Percent,
  currency: Money['currency'],
): FinalAccountLine {
  const billed = billedValue(item);
  const key = itemCodeKey(item.code);

  let measuredValue = zeroMoney(currency);
  let weight = 0;
  let weighted = 0;

  for (const pkg of context.register.packages) {
    const allocation = pkg.allocations.find((a) => itemCodeKey(a.code) === key);
    if (allocation === undefined) continue;
    const measured =
      measuredQuantity(context.records, asAt, { packageId: pkg.id, code: item.code }) ?? null;
    if (measured !== null) {
      measuredValue = sumMoney([measuredValue, extend(item.rate, measured)], currency);
    }
    const share = allocation.quantity.milli;
    weight += share;
    weighted += share * (fractions.get(pkg.id) ?? 0);
  }

  const complete = weight === 0 ? 0 : weighted / weight;
  const canExtrapolate =
    isEarnable(item) && complete >= percentFraction(threshold) && measuredValue.minor > 0;
  const trend = canExtrapolate
    ? { minor: Math.round(measuredValue.minor / complete), currency }
    : billed;
  const forecast = canExtrapolate ? maxMoney(trend, measuredValue) : billed;

  return {
    code: item.code,
    description: item.description,
    billed,
    measured: measuredValue,
    percentComplete: Number((complete * 100).toFixed(2)),
    forecast,
    movement: subtractMoney(forecast, billed),
    extrapolated: canExtrapolate,
  };
}

/** Lines whose forecast has moved away from the bill, worst first. */
export function movingLines(account: FinalAccount): FinalAccountLine[] {
  return account.lines
    .filter((line) => line.movement.minor !== 0)
    .sort((a, b) => Math.abs(b.movement.minor) - Math.abs(a.movement.minor));
}

export function describeLine(line: FinalAccountLine): string {
  const basis = line.extrapolated ? 'trend' : 'bill';
  return `${formatItemCode(line.code)} ${String(line.percentComplete)}% complete, on the ${basis}`;
}
