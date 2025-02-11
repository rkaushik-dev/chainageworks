/**
 * A small seeded generator, so the property checks are repeatable.
 *
 * Not cryptographic and not trying to be. What matters is that a failure can be
 * reproduced from the seed printed with it, which a generator seeded from the
 * clock cannot give.
 */
export interface Rng {
  next(): number;
  int(low: number, high: number): number;
  pick<T>(from: readonly T[]): T;
  bool(chance?: number): boolean;
}

export function rng(seed: number): Rng {
  let state = seed >>> 0 || 1;
  const next = (): number => {
    // xorshift32, which is plenty for shuffling a few hundred test values.
    state ^= state << 13;
    state >>>= 0;
    state ^= state >>> 17;
    state ^= state << 5;
    state >>>= 0;
    return state / 0x1_0000_0000;
  };
  return {
    next,
    int(low: number, high: number): number {
      return low + Math.floor(next() * (high - low + 1));
    },
    pick<T>(from: readonly T[]): T {
      const value = from[Math.floor(next() * from.length)];
      if (value === undefined) {
        throw new Error('cannot pick from an empty list');
      }
      return value;
    },
    bool(chance = 0.5): boolean {
      return next() < chance;
    },
  };
}

/** A list of n values built from the generator. */
export function sample<T>(count: number, make: (index: number) => T): T[] {
  return Array.from({ length: count }, (_, index) => make(index));
}
