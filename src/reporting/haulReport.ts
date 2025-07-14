import type { Allocation, HaulSite } from '../domain/earthworks/allocation.js';
import { haulMoment, isBalanced, movementsFrom } from '../domain/earthworks/allocation.js';
import { formatMoney } from '../domain/money/money.js';
import { formatQuantity, quantityAmount } from '../domain/quantity/quantity.js';
import { renderHeading, renderPairs, renderTable } from './format/table.js';

/**
 * The haul plan as the sheet that goes to the plant manager.
 *
 * Ordered by what it costs rather than by where it is on the alignment,
 * because the first three lines are where the money is and a plan sorted by
 * chainage buries them in the middle.
 */
export function renderHaulPlan(allocation: Allocation, sites: readonly HaulSite[]): string {
  const nameOf = new Map(sites.map((site) => [site.id, site.name]));
  const sections: string[] = [
    renderHeading('haul plan'),
    '',
    renderPairs([
      ['movements', String(allocation.movements.length)],
      ['haul moment', `${haulMoment(allocation).toFixed(0)} m3 km`],
      ['cost', formatMoney(allocation.cost)],
      ['balanced', isBalanced(allocation) ? 'yes' : 'no'],
      ['unplaced', formatQuantity(allocation.unplaced)],
      ['unfilled', formatQuantity(allocation.unfilled)],
      ['spare tip capacity', formatQuantity(allocation.spareTipCapacity)],
    ]),
  ];

  if (allocation.movements.length > 0) {
    const ordered = [...allocation.movements].sort((a, b) => b.cost.minor - a.cost.minor);
    sections.push(
      '',
      renderTable(
        [
          { heading: 'from', width: 26 },
          { heading: 'to', width: 26 },
          { heading: 'volume', align: 'right' },
          { heading: 'km', align: 'right' },
          { heading: 'haul', align: 'right' },
          { heading: 'site', align: 'right' },
          { heading: 'cost', align: 'right' },
        ],
        ordered.map((movement) => [
          `${movement.fromId} ${nameOf.get(movement.fromId) ?? ''}`.trim(),
          `${movement.toId} ${nameOf.get(movement.toId) ?? ''}`.trim(),
          formatQuantity(movement.volume),
          String(movement.km),
          formatMoney(movement.haulCost),
          formatMoney(movement.siteCost),
          formatMoney(movement.cost),
        ]),
      ),
    );
  }
  return sections.join('\n');
}

/** What has to leave each source, which is what a muck away schedule is. */
export function muckAwaySchedule(allocation: Allocation, sites: readonly HaulSite[]): string[] {
  return sites
    .filter((site) => site.kind === 'cut' || site.kind === 'borrow')
    .map((site) => {
      const out = movementsFrom(allocation, site.id);
      const total = out.reduce((sum, movement) => sum + quantityAmount(movement.volume), 0);
      const to = out.map((movement) => movement.toId).join(', ');
      return out.length === 0
        ? `${site.id} ${site.name}, nowhere to go`
        : `${site.id} ${site.name}, ${String(total)} m3 to ${to}`;
    });
}
