import type { CwError } from './errors.js';

/**
 * Parsing site paperwork is the one place where throwing is wrong: an import
 * run has to report every bad row, not stop on the first one. Those paths
 * return a Result and the callers collect the failures.
 */
export type Result<T, E = CwError> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly error: E };

export function ok<T>(value: T): Result<T, never> {
  return { ok: true, value };
}

export function err<E>(error: E): Result<never, E> {
  return { ok: false, error };
}

export function isOk<T, E>(r: Result<T, E>): r is { ok: true; value: T } {
  return r.ok;
}

export function isErr<T, E>(r: Result<T, E>): r is { ok: false; error: E } {
  return !r.ok;
}

export function mapResult<T, U, E>(r: Result<T, E>, f: (value: T) => U): Result<U, E> {
  return r.ok ? ok(f(r.value)) : r;
}

export function flatMapResult<T, U, E>(
  r: Result<T, E>,
  f: (value: T) => Result<U, E>,
): Result<U, E> {
  return r.ok ? f(r.value) : r;
}

export function unwrap<T>(r: Result<T, CwError>): T {
  if (r.ok) return r.value;
  throw r.error;
}

export function unwrapOr<T, E>(r: Result<T, E>, fallback: T): T {
  return r.ok ? r.value : fallback;
}

/**
 * Split a batch into the rows that parsed and the ones that did not. Order is
 * preserved inside each half so an import report can quote line numbers in the
 * order the file had them.
 */
export function partitionResults<T, E>(
  results: readonly Result<T, E>[],
): { values: T[]; errors: E[] } {
  const values: T[] = [];
  const errors: E[] = [];
  for (const r of results) {
    if (r.ok) values.push(r.value);
    else errors.push(r.error);
  }
  return { values, errors };
}

/** All-or-nothing: the first failure wins and the rest are not reported. */
export function collectResults<T, E>(results: readonly Result<T, E>[]): Result<T[], E> {
  const values: T[] = [];
  for (const r of results) {
    if (!r.ok) return r;
    values.push(r.value);
  }
  return ok(values);
}
