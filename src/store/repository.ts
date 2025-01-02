import { ConflictError, NotFoundError } from '../errors.js';

/**
 * A keyed collection of one kind of record.
 *
 * Deliberately thin. Everything that decides anything lives in the domain, and
 * the store's whole job is to hand the same objects back. The one thing it does
 * enforce is that an id is unique, because two work packages with the same id
 * would be reconciled against the bill twice.
 */
export interface Repository<T> {
  readonly kind: string;
  /** How an item's key is read, so callers never have to guess the field. */
  readonly idOf: IdOf<T>;
  all(): T[];
  find(id: string): T | null;
  require(id: string): T;
  has(id: string): boolean;
  /** Insert, or replace what is already there. */
  put(item: T): T;
  /** Insert, refusing an id that is already taken. */
  add(item: T): T;
  remove(id: string): boolean;
  where(predicate: (item: T) => boolean): T[];
  count(): number;
  clear(): void;
}

export type IdOf<T> = (item: T) => string;

/**
 * Records come back sorted by id rather than in the order they arrived. A
 * report that lists packages in insertion order changes when somebody reimports
 * the same file, and that shows up as a diff nobody can explain.
 */
export function inMemoryRepository<T>(
  kind: string,
  idOf: IdOf<T>,
  seed: readonly T[] = [],
): Repository<T> {
  const items = new Map<string, T>();

  const repository: Repository<T> = {
    kind,
    idOf,
    all(): T[] {
      return [...items.entries()]
        .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
        .map(([, item]) => item);
    },
    find(id: string): T | null {
      return items.get(id) ?? null;
    },
    require(id: string): T {
      const found = items.get(id);
      if (found === undefined) {
        throw new NotFoundError(kind, id);
      }
      return found;
    },
    has(id: string): boolean {
      return items.has(id);
    },
    put(item: T): T {
      items.set(idOf(item), item);
      return item;
    },
    add(item: T): T {
      const id = idOf(item);
      if (items.has(id)) {
        throw new ConflictError(`a ${kind} with that id is already there`, { kind, id });
      }
      items.set(id, item);
      return item;
    },
    remove(id: string): boolean {
      return items.delete(id);
    },
    where(predicate: (item: T) => boolean): T[] {
      return repository.all().filter(predicate);
    },
    count(): number {
      return items.size;
    },
    clear(): void {
      items.clear();
    },
  };

  for (const item of seed) repository.add(item);
  return repository;
}

/** Put a batch in, replacing anything already under the same ids. */
export function putAll<T>(repository: Repository<T>, items: readonly T[]): number {
  for (const item of items) repository.put(item);
  return items.length;
}

/**
 * Add a batch, refusing the whole lot if any id is taken.
 *
 * All or nothing on purpose. A half applied import leaves the store in a state
 * no file on disk describes, and the next export writes that state out as if it
 * were what somebody meant.
 */
export function addAll<T>(repository: Repository<T>, items: readonly T[]): number {
  const existing = repository.all();
  try {
    for (const item of items) repository.add(item);
    return items.length;
  } catch (error) {
    repository.clear();
    putAll(repository, existing);
    throw error;
  }
}

export function removeAll<T>(repository: Repository<T>, ids: readonly string[]): number {
  let removed = 0;
  for (const id of ids) {
    if (repository.remove(id)) removed += 1;
  }
  return removed;
}

export function ids<T>(repository: Repository<T>): string[] {
  return repository.all().map((item) => repository.idOf(item));
}
