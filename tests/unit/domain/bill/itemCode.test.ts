import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../../src/errors.js';
import {
  codeDepth,
  compareItemCodes,
  formatItemCode,
  isAncestorCode,
  itemCode,
  itemCodeKey,
  itemCodesEqual,
  MAX_DIVISIONS,
  parentCode,
  parseItemCode,
  tryParseItemCode,
  WORK_CLASSES,
  workClassName,
} from '../../../../src/domain/bill/itemCode.js';

const c = parseItemCode;

describe('WORK_CLASSES', () => {
  it('covers the twenty six classes a civil bill uses', () => {
    expect(Object.keys(WORK_CLASSES)).toHaveLength(26);
    expect(WORK_CLASSES['E']).toBe('earthworks');
    expect(WORK_CLASSES['R']).toBe('roads and pavings');
  });
});

describe('itemCode', () => {
  it('holds a class, divisions and a suffix', () => {
    const code = itemCode('E', [4, 2, 3], '1');
    expect(code.workClass).toBe('E');
    expect(code.divisions).toEqual([4, 2, 3]);
    expect(code.suffix).toBe('1');
  });

  it('upper cases the class and the suffix', () => {
    const code = itemCode('e', [1], '12a');
    expect(code.workClass).toBe('E');
    expect(code.suffix).toBe('12A');
  });

  it('allows a bare class letter', () => {
    expect(codeDepth(itemCode('E'))).toBe(0);
  });

  it('refuses a letter that is not a work class', () => {
    expect(() => itemCode('1')).toThrow(/work class letter/);
    expect(() => itemCode('AA')).toThrow(/work class letter/);
  });

  it('refuses more than three divisions', () => {
    expect(() => itemCode('E', [1, 2, 3, 4])).toThrow(/at most three divisions/);
    expect(MAX_DIVISIONS).toBe(3);
  });

  it('refuses a division that is not a single digit', () => {
    expect(() => itemCode('E', [10])).toThrow(/single digit/);
    expect(() => itemCode('E', [-1])).toThrow(/single digit/);
    expect(() => itemCode('E', [1.5])).toThrow(/single digit/);
  });

  it('refuses a suffix with punctuation in it', () => {
    expect(() => itemCode('E', [1], 'a-b')).toThrow(/letters and digits only/);
  });

  it('names the class', () => {
    expect(workClassName(itemCode('F'))).toBe('in situ concrete');
  });
});

describe('parseItemCode', () => {
  it('reads the tight form', () => {
    expect(c('E423.1')).toEqual({ workClass: 'E', divisions: [4, 2, 3], suffix: '1' });
  });

  it('reads the spaced form off a bill page', () => {
    expect(c('E 4 2 3.1')).toEqual({ workClass: 'E', divisions: [4, 2, 3], suffix: '1' });
    expect(c(' E 4 2 3 . 1 ')).toEqual({ workClass: 'E', divisions: [4, 2, 3], suffix: '1' });
  });

  it('reads a code with no suffix and a code with no divisions', () => {
    expect(c('E423').suffix).toBeNull();
    expect(c('E').divisions).toEqual([]);
  });

  it('reads a lower case code', () => {
    expect(c('e423.1').workClass).toBe('E');
  });

  it('refuses more than three digits', () => {
    const r = tryParseItemCode('E4234');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.error.message).toMatch(/not a bill item code/);
  });

  it('refuses a code that does not start with a letter', () => {
    expect(() => parseItemCode('423')).toThrow(BadInputError);
    expect(() => parseItemCode('')).toThrow(BadInputError);
  });

  it('reports a letter that is not a work class', () => {
    const r = tryParseItemCode('E423');
    expect(r.ok).toBe(true);
  });
});

describe('formatItemCode', () => {
  it('writes the tight form by default', () => {
    expect(formatItemCode(c('E423.1'))).toBe('E423.1');
    expect(formatItemCode(c('E423'))).toBe('E423');
    expect(formatItemCode(c('E'))).toBe('E');
  });

  it('writes the spaced form on request', () => {
    expect(formatItemCode(c('E423.1'), true)).toBe('E 4 2 3.1');
    expect(formatItemCode(c('E'), true)).toBe('E');
  });

  it('round trips through the parser', () => {
    for (const text of ['E423.1', 'E423', 'E4', 'E', 'R712.14', 'I185.2A']) {
      expect(formatItemCode(c(text))).toBe(text);
    }
  });
});

describe('parentCode', () => {
  it('drops the suffix first', () => {
    expect(formatItemCode(parentCode(c('E423.1'))!)).toBe('E423');
  });

  it('then drops a division at a time', () => {
    expect(formatItemCode(parentCode(c('E423'))!)).toBe('E42');
    expect(formatItemCode(parentCode(c('E42'))!)).toBe('E4');
    expect(formatItemCode(parentCode(c('E4'))!)).toBe('E');
  });

  it('gives nothing above the class letter', () => {
    expect(parentCode(c('E'))).toBeNull();
  });
});

describe('isAncestorCode', () => {
  it('reads a heading as the ancestor of what sits under it', () => {
    expect(isAncestorCode(c('E'), c('E423.1'))).toBe(true);
    expect(isAncestorCode(c('E4'), c('E423'))).toBe(true);
    expect(isAncestorCode(c('E42'), c('E423'))).toBe(true);
    expect(isAncestorCode(c('E423'), c('E423.1'))).toBe(true);
  });

  it('is false for a code against itself', () => {
    expect(isAncestorCode(c('E423'), c('E423'))).toBe(false);
    expect(isAncestorCode(c('E'), c('E'))).toBe(false);
  });

  it('is false across classes and across branches', () => {
    expect(isAncestorCode(c('E4'), c('F423'))).toBe(false);
    expect(isAncestorCode(c('E42'), c('E433'))).toBe(false);
  });

  it('is false the wrong way round', () => {
    expect(isAncestorCode(c('E423'), c('E42'))).toBe(false);
  });

  it('is false when the supposed ancestor carries a suffix of its own', () => {
    expect(isAncestorCode(c('E42.1'), c('E423'))).toBe(false);
  });
});

describe('compareItemCodes', () => {
  it('orders by class first', () => {
    expect(compareItemCodes(c('E999'), c('F111'))).toBeLessThan(0);
  });

  it('orders by division', () => {
    expect(compareItemCodes(c('E123'), c('E132'))).toBeLessThan(0);
    expect(compareItemCodes(c('E423'), c('E413'))).toBeGreaterThan(0);
  });

  it('puts a heading before the items under it', () => {
    expect(compareItemCodes(c('E4'), c('E42'))).toBeLessThan(0);
    expect(compareItemCodes(c('E423'), c('E423.1'))).toBeLessThan(0);
  });

  it('orders numeric suffixes as numbers, not as text', () => {
    expect(compareItemCodes(c('E423.2'), c('E423.10'))).toBeLessThan(0);
  });

  it('puts a numeric suffix before a lettered one', () => {
    expect(compareItemCodes(c('E423.2'), c('E423.A'))).toBeLessThan(0);
    expect(compareItemCodes(c('E423.A'), c('E423.2'))).toBeGreaterThan(0);
    expect(compareItemCodes(c('E423.A'), c('E423.B'))).toBeLessThan(0);
  });

  it('is zero for the same code', () => {
    expect(compareItemCodes(c('E423.1'), c('E423.1'))).toBe(0);
  });

  it('sorts a bill into reading order', () => {
    const codes = ['E423.2', 'E4', 'F1', 'E423.1', 'E42', 'E423'].map(c);
    const sorted = [...codes].sort(compareItemCodes).map((x) => formatItemCode(x));
    expect(sorted).toEqual(['E4', 'E42', 'E423', 'E423.1', 'E423.2', 'F1']);
  });
});

describe('itemCodesEqual and itemCodeKey', () => {
  it('compares every part', () => {
    expect(itemCodesEqual(c('E423.1'), c('E 4 2 3 . 1'))).toBe(true);
    expect(itemCodesEqual(c('E423.1'), c('E423.2'))).toBe(false);
    expect(itemCodesEqual(c('E423'), c('E42'))).toBe(false);
  });

  it('builds a key that is unique per code', () => {
    expect(itemCodeKey(c('E423.1'))).toBe('E423.1');
    expect(itemCodeKey(c('E4'))).toBe('E4__.');
    expect(itemCodeKey(c('E42'))).not.toBe(itemCodeKey(c('E423')));
  });
});
