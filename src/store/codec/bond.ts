import type { RetentionBond } from '../../valuation/bond.js';
import { buildRetentionBond } from '../../valuation/bond.js';
import {
  dateFromJson,
  dateToJson,
  moneyFromJson,
  moneyToJson,
  needString,
  objectFromJson,
  percentFromJson,
  percentToJson,
} from './values.js';

export function retentionBondToJson(bond: RetentionBond): Record<string, unknown> {
  return {
    id: bond.id,
    surety: bond.surety,
    maximum: moneyToJson(bond.maximum),
    issuedOn: dateToJson(bond.issuedOn),
    expiresOn: dateToJson(bond.expiresOn),
    rate: percentToJson(bond.rate),
    reducesTo: bond.reducesTo === null ? null : moneyToJson(bond.reducesTo),
  };
}

export function retentionBondFromJson(value: unknown): RetentionBond | null {
  if (value === null || value === undefined) return null;
  const raw = objectFromJson(value, 'retention bond');
  const reducesTo = raw['reducesTo'];
  return buildRetentionBond({
    id: needString(raw['id'], 'bond id'),
    surety: needString(raw['surety'], 'surety'),
    maximum: moneyFromJson(raw['maximum']),
    issuedOn: dateFromJson(raw['issuedOn']),
    expiresOn: dateFromJson(raw['expiresOn']),
    rate: percentFromJson(raw['rate']),
    reducesTo: reducesTo === null || reducesTo === undefined ? null : moneyFromJson(reducesTo),
  });
}
