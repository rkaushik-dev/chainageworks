/**
 * Every failure this package raises deliberately carries a stable machine code.
 * The CLI prints the code next to the message and the HTTP layer maps it to a
 * status, so renaming one is a breaking change for both.
 */
export type ErrorCode = 'bad_input' | 'not_found' | 'conflict' | 'unsupported' | 'internal';

export interface ErrorDetails {
  readonly [key: string]: string | number | boolean | null | undefined;
}

export class CwError extends Error {
  readonly code: ErrorCode;
  readonly details: ErrorDetails;

  constructor(code: ErrorCode, message: string, details: ErrorDetails = {}) {
    super(message);
    this.name = new.target.name;
    this.code = code;
    this.details = details;
  }

  /** Shape used by both the JSON API and the `--json` CLI flag. */
  toJSON(): { code: ErrorCode; message: string; details: ErrorDetails } {
    return { code: this.code, message: this.message, details: this.details };
  }
}

/** The caller handed us something the domain cannot accept. */
export class BadInputError extends CwError {
  constructor(message: string, details: ErrorDetails = {}) {
    super('bad_input', message, details);
  }
}

/** A referenced record does not exist in the store. */
export class NotFoundError extends CwError {
  constructor(kind: string, id: string) {
    super('not_found', `no ${kind} with id ${id}`, { kind, id });
  }
}

/** The request is well formed but the current state refuses it. */
export class ConflictError extends CwError {
  constructor(message: string, details: ErrorDetails = {}) {
    super('conflict', message, details);
  }
}

/** A shape we know about but have not implemented for this contract type. */
export class UnsupportedError extends CwError {
  constructor(message: string, details: ErrorDetails = {}) {
    super('unsupported', message, details);
  }
}

export function isCwError(value: unknown): value is CwError {
  return value instanceof CwError;
}

/**
 * Wrap anything thrown by a lower layer so the outer boundary always sees a
 * CwError. Something that is already one passes through untouched, because the
 * code it carries is more specific than anything we could invent here.
 */
export function asCwError(value: unknown): CwError {
  if (isCwError(value)) return value;
  if (value instanceof Error) {
    return new CwError('internal', value.message, { cause: value.name });
  }
  return new CwError('internal', String(value));
}
