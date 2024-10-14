import { BadInputError, NotFoundError } from '../../errors.js';
import { percent } from '../money/percent.js';
import type { CreditProfile } from './profile.js';
import { buildCreditProfile } from './profile.js';

/**
 * The rules of credit that come with the tool.
 *
 * These are the ones a civil job uses over and over. A contract can define its
 * own and register them instead, but starting from an empty catalogue means
 * every job spends its first week writing out the same five profiles.
 */

export const SINGLE_STEP: CreditProfile = buildCreditProfile({
  id: 'single-step',
  name: 'complete or not',
  steps: [{ id: 'complete', name: 'complete', weight: percent(100) }],
});

export const PIPE_RUN: CreditProfile = buildCreditProfile({
  id: 'pipe-run',
  name: 'pipe laying',
  steps: [
    { id: 'excavate', name: 'excavate trench', weight: percent(25) },
    { id: 'bed', name: 'lay bedding', weight: percent(10), requiresPrevious: true },
    { id: 'lay', name: 'lay and joint pipe', weight: percent(30), requiresPrevious: true },
    {
      id: 'backfill',
      name: 'backfill and compact',
      weight: percent(25),
      requiresPrevious: true,
    },
    { id: 'test', name: 'pressure test', weight: percent(10), requiresPrevious: true },
  ],
});

export const BULK_EARTHWORKS: CreditProfile = buildCreditProfile({
  id: 'bulk-earthworks',
  name: 'bulk earthworks',
  steps: [
    { id: 'strip', name: 'strip topsoil', weight: percent(10) },
    { id: 'excavate', name: 'excavate to formation', weight: percent(45) },
    { id: 'place', name: 'place and compact', weight: percent(35), requiresPrevious: true },
    { id: 'trim', name: 'trim to level', weight: percent(10), requiresPrevious: true },
  ],
});

export const STRUCTURAL_CONCRETE: CreditProfile = buildCreditProfile({
  id: 'structural-concrete',
  name: 'in situ concrete',
  steps: [
    { id: 'formwork', name: 'erect formwork', weight: percent(30) },
    { id: 'reinforcement', name: 'fix reinforcement', weight: percent(25) },
    { id: 'pour', name: 'pour concrete', weight: percent(35), requiresPrevious: true },
    { id: 'strike', name: 'strike and cure', weight: percent(10), requiresPrevious: true },
  ],
});

export const PAVEMENT: CreditProfile = buildCreditProfile({
  id: 'pavement',
  name: 'flexible pavement',
  steps: [
    { id: 'subbase', name: 'lay sub base', weight: percent(25) },
    { id: 'base', name: 'lay base course', weight: percent(35), requiresPrevious: true },
    { id: 'binder', name: 'lay binder course', weight: percent(25), requiresPrevious: true },
    { id: 'surface', name: 'lay surface course', weight: percent(15), requiresPrevious: true },
  ],
});

export const DRAINAGE_STRUCTURE: CreditProfile = buildCreditProfile({
  id: 'drainage-structure',
  name: 'manhole or chamber',
  steps: [
    { id: 'base', name: 'cast base slab', weight: percent(20) },
    { id: 'chamber', name: 'build chamber', weight: percent(40), requiresPrevious: true },
    { id: 'slab', name: 'cover slab', weight: percent(20), requiresPrevious: true },
    {
      id: 'ironwork',
      name: 'set cover and frame',
      weight: percent(20),
      requiresPrevious: true,
    },
  ],
});

export const SITE_SETUP: CreditProfile = buildCreditProfile({
  id: 'site-setup',
  name: 'establish and remove',
  steps: [
    { id: 'mobilise', name: 'mobilise', weight: percent(60) },
    { id: 'maintain', name: 'maintain for the duration', weight: percent(30) },
    { id: 'demobilise', name: 'demobilise', weight: percent(10), requiresPrevious: true },
  ],
});

export const STANDARD_PROFILES: readonly CreditProfile[] = [
  SINGLE_STEP,
  PIPE_RUN,
  BULK_EARTHWORKS,
  STRUCTURAL_CONCRETE,
  PAVEMENT,
  DRAINAGE_STRUCTURE,
  SITE_SETUP,
];

/** Which profile a work class falls back to when the package does not say. */
const BY_WORK_CLASS: Readonly<Record<string, string>> = {
  A: 'site-setup',
  D: 'single-step',
  E: 'bulk-earthworks',
  F: 'structural-concrete',
  G: 'structural-concrete',
  H: 'structural-concrete',
  I: 'pipe-run',
  J: 'pipe-run',
  K: 'drainage-structure',
  R: 'pavement',
  U: 'structural-concrete',
};

export interface ProfileRegistry {
  readonly profiles: ReadonlyMap<string, CreditProfile>;
}

export function buildProfileRegistry(
  profiles: readonly CreditProfile[] = STANDARD_PROFILES,
): ProfileRegistry {
  const map = new Map<string, CreditProfile>();
  for (const profile of profiles) {
    if (map.has(profile.id)) {
      throw new BadInputError('two rules of credit share an id', { profile: profile.id });
    }
    map.set(profile.id, profile);
  }
  return { profiles: map };
}

export function findProfile(registry: ProfileRegistry, id: string): CreditProfile | null {
  return registry.profiles.get(id) ?? null;
}

export function requireProfile(registry: ProfileRegistry, id: string): CreditProfile {
  const profile = findProfile(registry, id);
  if (profile === null) {
    throw new NotFoundError('rule of credit', id);
  }
  return profile;
}

/**
 * The profile a work class defaults to. Anything not listed falls back to the
 * single step profile, which never overstates progress: it pays on completion
 * and nothing before.
 */
export function defaultProfileFor(registry: ProfileRegistry, workClass: string): CreditProfile {
  const id = BY_WORK_CLASS[workClass.toUpperCase()] ?? SINGLE_STEP.id;
  return requireProfile(registry, id);
}

export function registeredIds(registry: ProfileRegistry): string[] {
  return [...registry.profiles.keys()].sort();
}

/** A registry with one more profile in it, or one replaced. */
export function withProfile(
  registry: ProfileRegistry,
  profile: CreditProfile,
): ProfileRegistry {
  const kept = [...registry.profiles.values()].filter((p) => p.id !== profile.id);
  return buildProfileRegistry([...kept, profile]);
}
