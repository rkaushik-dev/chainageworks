import type { Bill } from '../../src/domain/bill/bill.js';
import { buildBill } from '../../src/domain/bill/bill.js';
import type { BillItem, BillItemKind } from '../../src/domain/bill/billItem.js';
import { buildBillItem } from '../../src/domain/bill/billItem.js';
import { parseItemCode } from '../../src/domain/bill/itemCode.js';
import { rate } from '../../src/domain/money/rate.js';
import { quantity } from '../../src/domain/quantity/quantity.js';
import type { UnitCode } from '../../src/domain/quantity/unit.js';

export interface ItemSpec {
  readonly code: string;
  readonly description?: string;
  readonly rate: number;
  readonly quantity: number;
  readonly unit: UnitCode;
  readonly kind?: BillItemKind;
}

export function item(spec: ItemSpec): BillItem {
  return buildBillItem({
    code: parseItemCode(spec.code),
    description: spec.description ?? `item ${spec.code}`,
    rate: rate(spec.rate, 'GBP', spec.unit),
    quantity: quantity(spec.quantity, spec.unit),
    kind: spec.kind ?? 'measured',
  });
}

export function bill(specs: readonly ItemSpec[], id = 'B1'): Bill {
  return buildBill({
    id,
    title: 'A1 improvement, bill 1',
    currency: 'GBP',
    items: specs.map(item),
  });
}

/**
 * A small but realistic bill: earthworks, concrete and roads, with one
 * provisional item and one lump sum, so every code path has a case.
 */
export const SAMPLE_SPECS: readonly ItemSpec[] = [
  {
    code: 'A213.1',
    description: 'establish and remove site accommodation',
    rate: 48_500,
    quantity: 1,
    unit: 'sum',
    kind: 'lump',
  },
  {
    code: 'E423.1',
    description: 'excavation for cuttings, maximum depth 2 to 5 m',
    rate: 4.85,
    quantity: 12_400,
    unit: 'm3',
  },
  {
    code: 'E423.2',
    description: 'excavation for cuttings, maximum depth 5 to 10 m',
    rate: 5.4,
    quantity: 3200,
    unit: 'm3',
  },
  {
    code: 'E532.1',
    description: 'filling to embankments, selected excavated material',
    rate: 3.15,
    quantity: 9800,
    unit: 'm3',
  },
  {
    code: 'E423.9',
    description: 'rock excavation, provisional',
    rate: 28,
    quantity: 500,
    unit: 'm3',
    kind: 'provisional',
  },
  {
    code: 'F624.1',
    description: 'formwork, plane vertical, width over 1.22 m',
    rate: 32.5,
    quantity: 640,
    unit: 'm2',
  },
  {
    code: 'R712.1',
    description: 'sub base, depth 225 mm',
    rate: 11.4,
    quantity: 4000,
    unit: 'm2',
  },
  {
    code: 'R712.2',
    description: 'base, dense bitumen macadam, depth 120 mm',
    rate: 18.75,
    quantity: 4000,
    unit: 'm2',
  },
];

export const sampleBill = (): Bill => bill(SAMPLE_SPECS);
