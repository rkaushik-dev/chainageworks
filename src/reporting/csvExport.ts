import { billAsAt } from '../domain/bill/revision.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatChainageRange } from '../domain/chainage/range.js';
import { moneyMajor } from '../domain/money/money.js';
import { rateMajor } from '../domain/money/rate.js';
import { percentValue } from '../domain/money/percent.js';
import { quantityAmount } from '../domain/quantity/quantity.js';
import { effectiveRecords } from '../domain/survey/record.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { writeCsv } from '../io/csv.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import type { ProgressContext } from '../progress/context.js';
import type { Certificate } from '../valuation/certificate.js';
import type { Outturn } from '../valuation/outturn.js';

/**
 * Numbers go out unformatted, with no symbol and no thousands separator.
 *
 * A spreadsheet is the only reader of these files and it wants a number it can
 * add up. Sending "£16,152.50" produces a column of text that silently sums to
 * nothing, which is the single most common way a csv export wastes an
 * afternoon.
 */
function amount(minor: number): string {
  return (minor / 100).toFixed(2);
}

/** The bill in force at a date, priced. */
export function billCsv(context: ProgressContext, asAt: SiteDate): string {
  const bill = billAsAt(context.billHistory, asAt);
  return writeCsv(
    ['code', 'description', 'unit', 'quantity', 'rate', 'kind', 'value'],
    bill.items.map((item) => [
      formatItemCode(item.code),
      item.description,
      item.unit,
      String(quantityAmount(item.quantity)),
      String(rateMajor(item.rate)),
      item.kind,
      amount(Math.round(quantityAmount(item.quantity) * rateMajor(item.rate) * 100)),
    ]),
  );
}

/** The survey book as it stands at a date, superseded records left out. */
export function recordsCsv(context: ProgressContext, asAt: SiteDate): string {
  return writeCsv(
    [
      'record',
      'package',
      'code',
      'step',
      'from',
      'to',
      'quantity',
      'unit',
      'measured on',
      'booked by',
      'method',
    ],
    effectiveRecords(context.records, asAt).map((record) => [
      record.id,
      record.packageId,
      formatItemCode(record.code),
      record.stepId,
      formatChainageRange(record.extent).split(' to ')[0] ?? '',
      formatChainageRange(record.extent).split(' to ')[1] ?? '',
      String(quantityAmount(record.quantity)),
      record.quantity.unit,
      formatSiteDate(record.measuredOn),
      record.bookedBy,
      record.method,
    ]),
  );
}

/**
 * The certificate as a spreadsheet, one row per allocation.
 *
 * Flattened rather than nested, because that is the shape a pivot table wants.
 * The package columns repeat down the file on purpose: a reader that groups
 * them itself can, and one that does not still gets a table that adds up.
 */
export function certificateCsv(certificate: Certificate): string {
  return writeCsv(
    [
      'valuation',
      'cut off',
      'package',
      'percent',
      'code',
      'allocated',
      'measured',
      'earned',
      'value',
      'capped',
    ],
    certificate.earnings.flatMap((earning) =>
      earning.allocations.map((allocation) => [
        String(certificate.period.number),
        formatSiteDate(certificate.period.cutOff),
        earning.packageId,
        String(percentValue(earning.fraction)),
        formatItemCode(allocation.code),
        String(quantityAmount(allocation.allocated)),
        String(quantityAmount(allocation.measured)),
        String(quantityAmount(allocation.earnedQuantity)),
        amount(allocation.earnedValue.minor),
        allocation.cappedByMeasurement ? 'yes' : 'no',
      ]),
    ),
  );
}

/** The final account, one row an item, for the spreadsheet nobody escapes. */
export function outturnCsv(outturn: Outturn): string {
  return writeCsv(
    ['code', 'description', 'billed', 'measured', 'forecast', 'movement', 'percent', 'basis'],
    outturn.account.lines.map((line) => [
      formatItemCode(line.code),
      line.description,
      amount(line.billed.minor),
      amount(line.measured.minor),
      amount(line.forecast.minor),
      amount(line.movement.minor),
      String(line.percentComplete),
      line.extrapolated ? 'trend' : 'bill',
    ]),
  );
}

/** What the numbers in a money column mean, for anybody reading them back. */
export function certificateTotalsCsv(certificate: Certificate): string {
  return writeCsv(
    ['item', 'amount'],
    [
      ['measured work', amount(certificate.measuredWork.minor)],
      ['materials on site', amount(certificate.materialsOnSite.minor)],
      ['daywork', amount(certificate.daywork.minor)],
      ['price adjustment', amount(certificate.priceAdjustment.amount.minor)],
      ['gross valuation', amount(certificate.gross.minor)],
      ['retention held', amount(certificate.retentionHeld.minor)],
      ['net valuation', amount(certificate.netValuation.minor)],
      ['previously certified', amount(certificate.previouslyCertified.minor)],
      ['amount due', amount(certificate.amountDue.minor)],
    ],
  );
}

export function outturnTotalMajor(outturn: Outturn): number {
  return moneyMajor(outturn.total);
}
