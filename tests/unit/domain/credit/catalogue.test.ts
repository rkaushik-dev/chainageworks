import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import {
  buildProfileRegistry,
  BULK_EARTHWORKS,
  defaultProfileFor,
  DRAINAGE_STRUCTURE,
  findProfile,
  PAVEMENT,
  PIPE_RUN,
  registeredIds,
  requireProfile,
  SINGLE_STEP,
  SITE_SETUP,
  STANDARD_PROFILES,
  STRUCTURAL_CONCRETE,
  withProfile,
} from '../../../../src/domain/credit/catalogue.js';
import { buildCreditProfile, earnedFraction } from '../../../../src/domain/credit/profile.js';
import { percent, percentValue } from '../../../../src/domain/money/percent.js';

const registry = buildProfileRegistry();

describe('the standard profiles', () => {
  it('carries the seven a civil job keeps needing', () => {
    expect(STANDARD_PROFILES.map((p) => p.id)).toEqual([
      'single-step',
      'pipe-run',
      'bulk-earthworks',
      'structural-concrete',
      'pavement',
      'drainage-structure',
      'site-setup',
    ]);
  });

  it('gives every one of them weights that add to a hundred', () => {
    for (const profile of STANDARD_PROFILES) {
      const all = profile.steps.map((s) => ({ stepId: s.id, fraction: percent(100) }));
      expect(percentValue(earnedFraction(profile, all))).toBe(100);
    }
  });

  it('pays the single step profile nothing until it is finished', () => {
    expect(percentValue(earnedFraction(SINGLE_STEP, []))).toBe(0);
    const half = [{ stepId: 'complete', fraction: percent(50) }];
    expect(percentValue(earnedFraction(SINGLE_STEP, half))).toBe(50);
  });

  it('orders the pipe run so nothing can be claimed out of sequence', () => {
    const backfillOnly = [{ stepId: 'backfill', fraction: percent(100) }];
    expect(percentValue(earnedFraction(PIPE_RUN, backfillOnly))).toBe(0);
  });

  it('lets earthworks strip topsoil before anything else has happened', () => {
    const stripOnly = [{ stepId: 'strip', fraction: percent(100) }];
    expect(percentValue(earnedFraction(BULK_EARTHWORKS, stripOnly))).toBe(10);
  });

  it('lets concrete formwork and reinforcement run in either order', () => {
    const steel = [{ stepId: 'reinforcement', fraction: percent(100) }];
    expect(percentValue(earnedFraction(STRUCTURAL_CONCRETE, steel))).toBe(25);
  });

  it('builds a pavement from the bottom up', () => {
    const surfaceOnly = [{ stepId: 'surface', fraction: percent(100) }];
    expect(percentValue(earnedFraction(PAVEMENT, surfaceOnly))).toBe(0);
  });

  it('lets site setup earn its maintenance without demobilising', () => {
    const running = [
      { stepId: 'mobilise', fraction: percent(100) },
      { stepId: 'maintain', fraction: percent(50) },
    ];
    expect(percentValue(earnedFraction(SITE_SETUP, running))).toBe(75);
  });
});

describe('the registry', () => {
  it('holds every standard profile', () => {
    expect(registeredIds(registry)).toEqual([
      'bulk-earthworks',
      'drainage-structure',
      'pavement',
      'pipe-run',
      'single-step',
      'site-setup',
      'structural-concrete',
    ]);
  });

  it('finds a profile by id', () => {
    expect(findProfile(registry, 'pipe-run')).toBe(PIPE_RUN);
    expect(findProfile(registry, 'nope')).toBeNull();
    expect(requireProfile(registry, 'pavement')).toBe(PAVEMENT);
  });

  it('throws for an id nothing carries', () => {
    expect(() => requireProfile(registry, 'nope')).toThrow(NotFoundError);
  });

  it('refuses two profiles sharing an id', () => {
    expect(() => buildProfileRegistry([PIPE_RUN, PIPE_RUN])).toThrow(/share an id/);
  });

  it('can be built empty and grown', () => {
    const empty = buildProfileRegistry([]);
    expect(registeredIds(empty)).toEqual([]);
    expect(registeredIds(withProfile(empty, PIPE_RUN))).toEqual(['pipe-run']);
  });

  it('replaces a profile with the same id rather than duplicating it', () => {
    const custom = buildCreditProfile({
      id: 'pipe-run',
      name: 'contract specific pipe laying',
      steps: [
        { id: 'dig', name: 'dig', weight: percent(50) },
        { id: 'lay', name: 'lay', weight: percent(50), requiresPrevious: true },
      ],
    });
    const next = withProfile(registry, custom);
    expect(registeredIds(next)).toHaveLength(7);
    expect(requireProfile(next, 'pipe-run').name).toBe('contract specific pipe laying');
    expect(requireProfile(registry, 'pipe-run').name).toBe('pipe laying');
  });
});

describe('defaultProfileFor', () => {
  it('maps the classes that have an obvious sequence', () => {
    expect(defaultProfileFor(registry, 'E')).toBe(BULK_EARTHWORKS);
    expect(defaultProfileFor(registry, 'F')).toBe(STRUCTURAL_CONCRETE);
    expect(defaultProfileFor(registry, 'I')).toBe(PIPE_RUN);
    expect(defaultProfileFor(registry, 'K')).toBe(DRAINAGE_STRUCTURE);
    expect(defaultProfileFor(registry, 'R')).toBe(PAVEMENT);
    expect(defaultProfileFor(registry, 'A')).toBe(SITE_SETUP);
  });

  it('ignores the case of the class letter', () => {
    expect(defaultProfileFor(registry, 'e')).toBe(BULK_EARTHWORKS);
  });

  it('falls back to complete or not, which never overstates progress', () => {
    expect(defaultProfileFor(registry, 'T')).toBe(SINGLE_STEP);
    expect(defaultProfileFor(registry, 'X')).toBe(SINGLE_STEP);
  });
});
