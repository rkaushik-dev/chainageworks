import { describe, expect, it } from 'vitest';
import { BadInputError, CwError } from '../../src/errors.js';
import {
  collectResults,
  err,
  flatMapResult,
  isErr,
  isOk,
  mapResult,
  ok,
  partitionResults,
  unwrap,
  unwrapOr,
} from '../../src/result.js';

describe('ok and err', () => {
  it('carry the value or the error under a discriminant', () => {
    const good = ok(42);
    expect(good).toEqual({ ok: true, value: 42 });

    const bad = err(new BadInputError('nope'));
    expect(bad.ok).toBe(false);
    if (!bad.ok) expect(bad.error.message).toBe('nope');
  });

  it('narrows through the guards', () => {
    const good = ok('yes');
    const bad = err('no');
    expect(isOk(good)).toBe(true);
    expect(isErr(good)).toBe(false);
    expect(isOk(bad)).toBe(false);
    expect(isErr(bad)).toBe(true);
  });
});

describe('mapResult', () => {
  it('applies the function to a value', () => {
    expect(mapResult(ok(2), (n) => n * 3)).toEqual({ ok: true, value: 6 });
  });

  it('leaves an error untouched and does not call the function', () => {
    let called = false;
    const out = mapResult(err('boom'), () => {
      called = true;
      return 1;
    });
    expect(out).toEqual({ ok: false, error: 'boom' });
    expect(called).toBe(false);
  });
});

describe('flatMapResult', () => {
  it('chains two fallible steps', () => {
    const out = flatMapResult(ok(4), (n) => (n > 0 ? ok(n / 2) : err('negative')));
    expect(out).toEqual({ ok: true, value: 2 });
  });

  it('reports the inner failure', () => {
    const out = flatMapResult(ok(-4), (n) => (n > 0 ? ok(n / 2) : err('negative')));
    expect(out).toEqual({ ok: false, error: 'negative' });
  });

  it('short circuits on an outer failure', () => {
    const out = flatMapResult(err('outer'), () => ok(1));
    expect(out).toEqual({ ok: false, error: 'outer' });
  });
});

describe('unwrap', () => {
  it('returns the value', () => {
    expect(unwrap(ok('here'))).toBe('here');
  });

  it('throws the error it holds', () => {
    const e = new CwError('conflict', 'locked');
    expect(() => unwrap(err(e))).toThrow(e);
  });
});

describe('unwrapOr', () => {
  it('prefers the value and falls back on failure', () => {
    expect(unwrapOr(ok(9), 0)).toBe(9);
    expect(unwrapOr(err('x'), 0)).toBe(0);
  });
});

describe('partitionResults', () => {
  it('splits a batch and keeps the order inside each half', () => {
    const batch = [ok(1), err('a'), ok(2), err('b'), ok(3)];
    expect(partitionResults(batch)).toEqual({ values: [1, 2, 3], errors: ['a', 'b'] });
  });

  it('handles an empty batch', () => {
    expect(partitionResults([])).toEqual({ values: [], errors: [] });
  });
});

describe('collectResults', () => {
  it('gathers every value when they all pass', () => {
    expect(collectResults([ok(1), ok(2)])).toEqual({ ok: true, value: [1, 2] });
  });

  it('reports only the first failure', () => {
    expect(collectResults([ok(1), err('first'), err('second')])).toEqual({
      ok: false,
      error: 'first',
    });
  });
});
