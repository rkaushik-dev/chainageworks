import { describe, expect, it } from 'vitest';
import {
  asCwError,
  BadInputError,
  ConflictError,
  CwError,
  isCwError,
  NotFoundError,
  UnsupportedError,
} from '../../src/errors.js';

describe('CwError', () => {
  it('keeps the code and the details it was given', () => {
    const e = new CwError('bad_input', 'chainage runs backwards', { from: 120, to: 40 });
    expect(e.code).toBe('bad_input');
    expect(e.message).toBe('chainage runs backwards');
    expect(e.details).toEqual({ from: 120, to: 40 });
  });

  it('names itself after the concrete subclass', () => {
    expect(new BadInputError('nope').name).toBe('BadInputError');
    expect(new NotFoundError('package', 'WP-01').name).toBe('NotFoundError');
    expect(new CwError('internal', 'nope').name).toBe('CwError');
  });

  it('defaults details to an empty object rather than undefined', () => {
    expect(new BadInputError('nope').details).toEqual({});
  });

  it('serialises to the wire shape', () => {
    const e = new ConflictError('already certified', { certificate: 7 });
    expect(e.toJSON()).toEqual({
      code: 'conflict',
      message: 'already certified',
      details: { certificate: 7 },
    });
  });

  it('is a real Error so stacks and instanceof still work', () => {
    const e = new UnsupportedError('lump sum bills are not measured');
    expect(e).toBeInstanceOf(Error);
    expect(e).toBeInstanceOf(CwError);
    expect(typeof e.stack).toBe('string');
  });
});

describe('NotFoundError', () => {
  it('builds a message from the kind and the id', () => {
    const e = new NotFoundError('bill item', 'E421.3');
    expect(e.message).toBe('no bill item with id E421.3');
    expect(e.details).toEqual({ kind: 'bill item', id: 'E421.3' });
  });
});

describe('isCwError', () => {
  it('accepts our errors and rejects everything else', () => {
    expect(isCwError(new BadInputError('x'))).toBe(true);
    expect(isCwError(new Error('x'))).toBe(false);
    expect(isCwError('x')).toBe(false);
    expect(isCwError(null)).toBe(false);
    expect(isCwError(undefined)).toBe(false);
  });
});

describe('asCwError', () => {
  it('passes one of ours through by identity', () => {
    const original = new ConflictError('locked');
    expect(asCwError(original)).toBe(original);
  });

  it('wraps a plain error and records the original name', () => {
    const wrapped = asCwError(new TypeError('not a number'));
    expect(wrapped.code).toBe('internal');
    expect(wrapped.message).toBe('not a number');
    expect(wrapped.details).toEqual({ cause: 'TypeError' });
  });

  it('stringifies anything that is not an error at all', () => {
    const wrapped = asCwError('disk went away');
    expect(wrapped.code).toBe('internal');
    expect(wrapped.message).toBe('disk went away');
    expect(wrapped.details).toEqual({});
  });
});
