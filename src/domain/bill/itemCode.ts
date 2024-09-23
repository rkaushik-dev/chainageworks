import { BadInputError } from '../../errors.js';
import type { Result } from '../../result.js';
import { err, ok } from '../../result.js';

/**
 * The work classification a civil bill is written to.
 *
 * These are the CESMM classes. They are not decoration: the class letter is
 * what decides which measurement rules apply to an item, so it has to survive
 * parsing rather than being flattened into the description.
 */
export const WORK_CLASSES: Readonly<Record<string, string>> = {
  A: 'general items',
  B: 'ground investigation',
  C: 'geotechnical and other specialist processes',
  D: 'demolition and site clearance',
  E: 'earthworks',
  F: 'in situ concrete',
  G: 'concrete ancillaries',
  H: 'precast concrete',
  I: 'pipework, pipes',
  J: 'pipework, fittings and valves',
  K: 'pipework, manholes and ancillaries',
  L: 'pipework, supports and protection',
  M: 'structural metalwork',
  N: 'miscellaneous metalwork',
  O: 'timber',
  P: 'piles',
  Q: 'piling ancillaries',
  R: 'roads and pavings',
  S: 'rail track',
  T: 'tunnels',
  U: 'brickwork, blockwork and masonry',
  V: 'painting',
  W: 'waterproofing',
  X: 'miscellaneous work',
  Y: 'sewer and water main renovation',
  Z: 'simple building works',
};

export const MAX_DIVISIONS = 3;

/**
 * A bill item code: a class letter, up to three division digits, and the
 * contractor's own numbering after a dot.
 */
export interface ItemCode {
  readonly workClass: string;
  readonly divisions: readonly number[];
  readonly suffix: string | null;
}

const CODE_TEXT = /^([A-Za-z])\s*((?:\s*\d){0,3})\s*(?:\.\s*([A-Za-z0-9]+))?$/;

export function itemCode(
  workClass: string,
  divisions: readonly number[] = [],
  suffix: string | null = null,
): ItemCode {
  const upper = workClass.toUpperCase();
  if (!Object.prototype.hasOwnProperty.call(WORK_CLASSES, upper)) {
    throw new BadInputError('not a work class letter', { workClass });
  }
  if (divisions.length > MAX_DIVISIONS) {
    throw new BadInputError('an item code carries at most three divisions', {
      divisions: divisions.length,
    });
  }
  for (const division of divisions) {
    if (!Number.isInteger(division) || division < 0 || division > 9) {
      throw new BadInputError('a division is a single digit', { division });
    }
  }
  if (suffix !== null && !/^[A-Za-z0-9]+$/.test(suffix)) {
    throw new BadInputError('a code suffix is letters and digits only', { suffix });
  }
  return { workClass: upper, divisions: [...divisions], suffix: suffix?.toUpperCase() ?? null };
}

export function tryParseItemCode(text: string): Result<ItemCode> {
  const trimmed = text.trim();
  const m = CODE_TEXT.exec(trimmed);
  if (m === null) {
    return err(new BadInputError('not a bill item code', { text: trimmed }));
  }
  const digits = (m[2] ?? '').replace(/\s+/g, '');
  try {
    return ok(
      itemCode(
        m[1] ?? '',
        [...digits].map((c) => Number(c)),
        m[3] ?? null,
      ),
    );
  } catch (e) {
    return err(e as BadInputError);
  }
}

export function parseItemCode(text: string): ItemCode {
  const r = tryParseItemCode(text);
  if (!r.ok) throw r.error;
  return r.value;
}

export function formatItemCode(code: ItemCode, spaced = false): string {
  const digits = code.divisions.join(spaced ? ' ' : '');
  const head =
    spaced && digits !== '' ? `${code.workClass} ${digits}` : code.workClass + digits;
  return code.suffix === null ? head : `${head}.${code.suffix}`;
}

export function workClassName(code: ItemCode): string {
  return WORK_CLASSES[code.workClass] ?? 'unknown';
}

export function codeDepth(code: ItemCode): number {
  return code.divisions.length;
}

/** The code one level up. A bare class letter has no parent. */
export function parentCode(code: ItemCode): ItemCode | null {
  if (code.suffix !== null) {
    return { workClass: code.workClass, divisions: code.divisions, suffix: null };
  }
  if (code.divisions.length === 0) return null;
  return {
    workClass: code.workClass,
    divisions: code.divisions.slice(0, -1),
    suffix: null,
  };
}

/**
 * Whether the first code is a heading the second one sits under. A code is not
 * its own ancestor, which is what the bill rollup relies on to avoid counting
 * a heading's own value twice.
 */
export function isAncestorCode(ancestor: ItemCode, code: ItemCode): boolean {
  if (ancestor.workClass !== code.workClass) return false;
  if (ancestor.suffix !== null) return false;
  if (ancestor.divisions.length > code.divisions.length) return false;
  if (ancestor.divisions.length === code.divisions.length && code.suffix === null) return false;
  return ancestor.divisions.every((d, i) => d === code.divisions[i]);
}

export function itemCodesEqual(a: ItemCode, b: ItemCode): boolean {
  return (
    a.workClass === b.workClass &&
    a.suffix === b.suffix &&
    a.divisions.length === b.divisions.length &&
    a.divisions.every((d, i) => d === b.divisions[i])
  );
}

/**
 * Bill order: class, then division by division, then the suffix. A shorter code
 * sorts before a longer one that starts the same way, so a heading always comes
 * before the items under it.
 */
export function compareItemCodes(a: ItemCode, b: ItemCode): number {
  if (a.workClass !== b.workClass) return a.workClass < b.workClass ? -1 : 1;
  const shared = Math.min(a.divisions.length, b.divisions.length);
  for (let i = 0; i < shared; i += 1) {
    const left = a.divisions[i] ?? 0;
    const right = b.divisions[i] ?? 0;
    if (left !== right) return left - right;
  }
  if (a.divisions.length !== b.divisions.length) {
    return a.divisions.length - b.divisions.length;
  }
  if (a.suffix === b.suffix) return 0;
  if (a.suffix === null) return -1;
  if (b.suffix === null) return 1;
  return compareSuffixes(a.suffix, b.suffix);
}

/** `2` sorts before `10`, which a plain string comparison gets wrong. */
function compareSuffixes(a: string, b: string): number {
  const aNum = /^\d+$/.test(a) ? Number(a) : null;
  const bNum = /^\d+$/.test(b) ? Number(b) : null;
  if (aNum !== null && bNum !== null) return aNum - bNum;
  if (aNum !== null) return -1;
  if (bNum !== null) return 1;
  return a < b ? -1 : 1;
}

/** A stable string key for maps and for sorting outside this module. */
export function itemCodeKey(code: ItemCode): string {
  const digits = code.divisions.map((d) => String(d)).join('');
  return `${code.workClass}${digits.padEnd(MAX_DIVISIONS, '_')}.${code.suffix ?? ''}`;
}
