import type { ErrorCode, ErrorDetails } from '../errors.js';
import { asCwError } from '../errors.js';

/**
 * The body every failing request comes back with.
 *
 * One shape for everything, so a client never has to guess. The code is the
 * domain's own machine code rather than the status, because two different
 * problems can share a status and only the code tells them apart.
 */
export interface Problem {
  readonly code: ErrorCode;
  readonly message: string;
  readonly details: ErrorDetails;
}

const STATUS_BY_CODE: Readonly<Record<ErrorCode, number>> = {
  bad_input: 400,
  not_found: 404,
  conflict: 409,
  unsupported: 501,
  internal: 500,
};

export function statusFor(code: ErrorCode): number {
  return STATUS_BY_CODE[code];
}

export interface Failure {
  readonly status: number;
  readonly body: Problem;
}

/**
 * Turn anything thrown anywhere below into a status and a body.
 *
 * Nothing else in the HTTP layer is allowed to choose a status. That is what
 * keeps the mapping honest: a new error code shows up here as a missing entry
 * in one table rather than as a 500 from a route nobody thought about.
 */
export function toFailure(error: unknown): Failure {
  const cwError = asCwError(error);
  return {
    status: statusFor(cwError.code),
    body: cwError.toJSON(),
  };
}

export function isClientFailure(failure: Failure): boolean {
  return failure.status >= 400 && failure.status < 500;
}

/** A one line summary for the request log. */
export function describeFailure(failure: Failure): string {
  return `${String(failure.status)} ${failure.body.code}: ${failure.body.message}`;
}
