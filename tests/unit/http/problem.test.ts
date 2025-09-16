import { describe, expect, it } from 'vitest';
import {
  BadInputError,
  ConflictError,
  CwError,
  NotFoundError,
  UnsupportedError,
} from '../../../src/errors.js';
import {
  describeFailure,
  isClientFailure,
  statusFor,
  toFailure,
} from '../../../src/http/problem.js';

describe('statusFor', () => {
  it('maps every code the domain can raise', () => {
    expect(statusFor('bad_input')).toBe(400);
    expect(statusFor('not_found')).toBe(404);
    expect(statusFor('conflict')).toBe(409);
    expect(statusFor('unsupported')).toBe(501);
    expect(statusFor('internal')).toBe(500);
  });
});

describe('toFailure', () => {
  it('carries the code, the message and the details through', () => {
    const failure = toFailure(new BadInputError('a station is written 12+340', { got: 'x' }));
    expect(failure.status).toBe(400);
    expect(failure.body.code).toBe('bad_input');
    expect(failure.body.message).toBe('a station is written 12+340');
    expect(failure.body.details).toEqual({ got: 'x' });
  });

  it('gives a not found error its own status', () => {
    const failure = toFailure(new NotFoundError('work package', 'WP-99'));
    expect(failure.status).toBe(404);
    expect(failure.body.message).toContain('WP-99');
  });

  it('gives a conflict its own status', () => {
    expect(toFailure(new ConflictError('two records claim the same id')).status).toBe(409);
  });

  it('gives an unsupported request its own status', () => {
    expect(toFailure(new UnsupportedError('that unit cannot be measured')).status).toBe(501);
  });

  it('turns an ordinary error into an internal failure', () => {
    const failure = toFailure(new Error('something fell over'));
    expect(failure.status).toBe(500);
    expect(failure.body.code).toBe('internal');
  });

  it('turns a thrown string into an internal failure rather than crashing', () => {
    const failure = toFailure('just a string');
    expect(failure.status).toBe(500);
    expect(failure.body.code).toBe('internal');
  });

  it('leaves a domain error that is already one alone', () => {
    const original = new CwError('conflict', 'already there', { id: 'WP-01' });
    expect(toFailure(original).body).toEqual(original.toJSON());
  });
});

describe('isClientFailure', () => {
  it('is true for everything the caller can fix', () => {
    expect(isClientFailure(toFailure(new BadInputError('bad')))).toBe(true);
    expect(isClientFailure(toFailure(new NotFoundError('package', 'WP-99')))).toBe(true);
    expect(isClientFailure(toFailure(new ConflictError('clash')))).toBe(true);
  });

  it('is false for everything the caller cannot', () => {
    expect(isClientFailure(toFailure(new Error('fell over')))).toBe(false);
    expect(isClientFailure(toFailure(new UnsupportedError('not built yet')))).toBe(false);
  });
});

describe('describeFailure', () => {
  it('reads as one line for a log', () => {
    expect(describeFailure(toFailure(new NotFoundError('subcontract', 'SC-99')))).toBe(
      '404 not_found: no subcontract with id SC-99',
    );
  });

  it('reads the same way for an internal failure', () => {
    expect(describeFailure(toFailure(new Error('fell over')))).toContain('500 internal:');
  });
});
