import { BadInputError } from '../../errors.js';
import type { HaulLink, HaulSite, SiteKind } from '../../domain/earthworks/allocation.js';
import {
  moneyFromJson,
  moneyToJson,
  needString,
  objectFromJson,
  optionalString,
  quantityFromJson,
  quantityToJson,
} from './values.js';

const KINDS: readonly SiteKind[] = ['cut', 'borrow', 'fill', 'tip'];

function kindFromJson(value: unknown): SiteKind {
  const text = needString(value, 'haul site kind');
  const found = KINDS.find((kind) => kind === text);
  if (found === undefined) {
    throw new BadInputError('not a kind of haul site this tool knows', { got: text });
  }
  return found;
}

export function haulSiteToJson(site: HaulSite): Record<string, unknown> {
  return {
    id: site.id,
    name: site.name,
    kind: site.kind,
    volume: quantityToJson(site.volume),
    unitCost: moneyToJson(site.unitCost),
  };
}

export function haulSiteFromJson(value: unknown): HaulSite {
  const raw = objectFromJson(value, 'haul site');
  return {
    id: needString(raw['id'], 'site id'),
    name: optionalString(raw['name']),
    kind: kindFromJson(raw['kind']),
    volume: quantityFromJson(raw['volume']),
    unitCost: moneyFromJson(raw['unitCost']),
  };
}

/**
 * Haul links are settings rather than records, so they ride on the job header.
 *
 * A distance between two sites is a fact about the ground, not about the
 * works, and it does not change when a lorry runs. Keeping it out of the
 * repositories means an import of movements cannot silently rewrite it.
 */
export function haulLinkToJson(link: HaulLink): Record<string, unknown> {
  return { fromId: link.fromId, toId: link.toId, km: link.km };
}

export function haulLinkFromJson(value: unknown): HaulLink {
  const raw = objectFromJson(value, 'haul link');
  const km = Number(raw['km']);
  if (!Number.isFinite(km) || km < 0) {
    throw new BadInputError('a haul distance cannot be negative', { km });
  }
  return {
    fromId: needString(raw['fromId'], 'from site'),
    toId: needString(raw['toId'], 'to site'),
    km,
  };
}
