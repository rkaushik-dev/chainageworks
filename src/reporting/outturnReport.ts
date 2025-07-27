import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatMoney } from '../domain/money/money.js';
import { formatPercent } from '../domain/money/percent.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { movingLines } from '../valuation/finalAccount.js';
import type { Outturn } from '../valuation/outturn.js';
import { isOverContractSum, outturnParts, partsAddUp } from '../valuation/outturn.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

/**
 * The anticipated final account as a page.
 *
 * The moving lines are limited to the worst few, because the point of the list
 * is to be read. A final account on a road job has several hundred items and
 * nearly all of them are within a few pounds of the bill, so printing them all
 * hides the six that matter behind the ones that do not.
 */
export function renderOutturn(outturn: Outturn, worst = 10): string {
  const sections: string[] = [
    renderHeading(`anticipated final account at ${formatSiteDate(outturn.asAt)}`),
    '',
    renderTable(
      [{ heading: 'item' }, { heading: 'amount', align: 'right' }],
      [
        ...outturnParts(outturn).map((part) => [part.name, formatMoney(part.amount)]),
        ['forecast outturn', formatMoney(outturn.total)],
      ],
    ),
    '',
    renderPairs([
      ['contract sum', formatMoney(outturn.contractSum)],
      ['movement', formatMoney(outturn.movement)],
      ['movement percent', formatPercent(outturn.movementPercent, 2)],
      ['position', isOverContractSum(outturn) ? 'over the contract sum' : 'inside it'],
      ['parts add up', partsAddUp(outturn) ? 'yes' : 'no'],
      [
        'price adjustment',
        outturn.priceAdjustment.provisional ? 'on a provisional index' : 'firm',
      ],
    ]),
  ];

  const moving = movingLines(outturn.account).slice(0, worst);
  if (moving.length > 0) {
    sections.push(
      '',
      renderHeading('items moving away from the bill', '-'),
      renderTable(
        [
          { heading: 'code' },
          { heading: 'description', width: 34 },
          { heading: 'billed', align: 'right' },
          { heading: 'forecast', align: 'right' },
          { heading: 'movement', align: 'right' },
          { heading: 'complete', align: 'right' },
          { heading: 'basis' },
        ],
        moving.map((line) => [
          formatItemCode(line.code),
          line.description,
          formatMoney(line.billed),
          formatMoney(line.forecast),
          formatMoney(line.movement),
          `${String(line.percentComplete)}%`,
          line.extrapolated ? 'trend' : 'bill',
        ]),
      ),
    );
  }
  return sections.join('\n');
}
