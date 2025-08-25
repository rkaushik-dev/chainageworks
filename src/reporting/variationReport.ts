import type { Bill } from '../domain/bill/bill.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatChainageRange } from '../domain/chainage/range.js';
import type { Money } from '../domain/money/money.js';
import { formatMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import { formatQuantity } from '../domain/quantity/quantity.js';
import type { SiteDate } from '../domain/time/siteDate.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import type { Variation, ValuationBasis } from '../domain/variation/variation.js';
import { isEffectiveAt, isUnagreed } from '../domain/variation/variation.js';
import { applyVariations } from '../domain/variation/apply.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

export interface VariationLine {
  readonly variation: Variation;
  /** What this one alone is worth against the unvaried bill. */
  readonly value: Money;
  readonly inForce: boolean;
  readonly agreed: boolean;
  /** The ways this variation is priced, which is what the argument is about. */
  readonly bases: readonly ValuationBasis[];
}

export interface VariationReport {
  readonly asAt: SiteDate;
  readonly lines: readonly VariationLine[];
  /** Everything in force at the date, valued together. */
  readonly inForce: Money;
  /** In force but nobody has agreed the price yet. */
  readonly unagreed: Money;
  /**
   * Orders proposed and not instructed. A count rather than a value on
   * purpose: an order nobody has instructed is worth nothing under the
   * contract, and putting a figure against it invites somebody to add it to
   * the forecast.
   */
  readonly proposed: number;
}

/**
 * The variation register, valued one order at a time and then together.
 *
 * Each order is valued on its own against the unvaried bill rather than
 * incrementally against the running total. Two orders that both move the same
 * item would otherwise report a value that depends on the order they happen to
 * be listed in, and a register that reorders itself when somebody sorts a
 * column is a register nobody trusts.
 */
export function buildVariationReport(
  bill: Bill,
  variations: readonly Variation[],
  asAt: SiteDate,
): VariationReport {
  const currency = bill.currency;
  const lines = variations.map((variation): VariationLine => {
    const inForce = isEffectiveAt(variation, asAt);
    return {
      variation,
      value: inForce ? applyVariations(bill, [variation], asAt).netValue : zeroMoney(currency),
      inForce,
      agreed: variation.agreedOn !== null,
      bases: [...new Set(variation.items.map((item) => item.basis))],
    };
  });

  const live = lines.filter((line) => line.inForce);
  return {
    asAt,
    lines,
    inForce: applyVariations(
      bill,
      live.map((line) => line.variation),
      asAt,
    ).netValue,
    unagreed: sumMoney(
      live.filter((line) => isUnagreed(line.variation)).map((line) => line.value),
      currency,
    ),
    proposed: lines.filter((line) => !line.inForce && line.variation.status === 'proposed')
      .length,
  };
}

export function renderVariationReport(report: VariationReport): string {
  const sections: string[] = [
    renderHeading(`variations at ${formatSiteDate(report.asAt)}`),
    '',
    renderPairs([
      ['orders on the register', String(report.lines.length)],
      ['in force', String(report.lines.filter((line) => line.inForce).length)],
      ['value in force', formatMoney(report.inForce)],
      ['not yet agreed', formatMoney(report.unagreed)],
      ['proposed, not instructed', String(report.proposed)],
    ]),
  ];

  if (report.lines.length > 0) {
    sections.push(
      '',
      renderTable(
        [
          { heading: 'order' },
          { heading: 'title', width: 34 },
          { heading: 'status' },
          { heading: 'where', width: 24 },
          { heading: 'basis' },
          { heading: 'value', align: 'right' },
        ],
        report.lines.map((line) => [
          line.variation.reference,
          line.variation.title,
          line.variation.status,
          line.variation.extent === null ? '' : formatChainageRange(line.variation.extent),
          line.bases.join(', '),
          formatMoney(line.value),
        ]),
      ),
    );
  }
  return sections.join('\n');
}

/** The orders in force that nobody has agreed a price for, dearest first. */
export function chaseAgreement(report: VariationReport): string[] {
  return report.lines
    .filter((line) => line.inForce && !line.agreed)
    .sort((a, b) => Math.abs(b.value.minor) - Math.abs(a.value.minor))
    .map(
      (line) =>
        `${line.variation.reference} ${line.variation.title}, ` +
        `${formatMoney(line.value)} not agreed`,
    );
}

/** Every item a variation touches, for the surveyor remeasuring it. */
export function touchedItems(report: VariationReport): string[] {
  const out = new Set<string>();
  for (const line of report.lines) {
    if (!line.inForce) continue;
    for (const item of line.variation.items) {
      out.add(`${formatItemCode(item.code)} ${formatQuantity(item.quantityChange)}`);
    }
  }
  return [...out].sort();
}
