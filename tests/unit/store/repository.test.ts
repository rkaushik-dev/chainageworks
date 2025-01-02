import { beforeEach, describe, expect, it } from 'vitest';
import { ConflictError, NotFoundError } from '../../../src/errors.js';
import type { Repository } from '../../../src/store/repository.js';
import {
  addAll,
  ids,
  inMemoryRepository,
  putAll,
  removeAll,
} from '../../../src/store/repository.js';

interface Thing {
  readonly id: string;
  readonly name: string;
}

const thing = (id: string, name = id): Thing => ({ id, name });

describe('inMemoryRepository', () => {
  let repository: Repository<Thing>;

  beforeEach(() => {
    repository = inMemoryRepository<Thing>('thing', (t) => t.id);
  });

  it('starts empty and knows its kind', () => {
    expect(repository.kind).toBe('thing');
    expect(repository.all()).toEqual([]);
    expect(repository.count()).toBe(0);
  });

  it('takes a seed', () => {
    const seeded = inMemoryRepository<Thing>('thing', (t) => t.id, [thing('b'), thing('a')]);
    expect(ids(seeded)).toEqual(['a', 'b']);
  });

  it('refuses a seed with a repeated id', () => {
    expect(() =>
      inMemoryRepository<Thing>('thing', (t) => t.id, [thing('a'), thing('a')]),
    ).toThrow(ConflictError);
  });

  it('adds and finds', () => {
    repository.add(thing('a'));
    expect(repository.find('a')).toEqual(thing('a'));
    expect(repository.has('a')).toBe(true);
    expect(repository.count()).toBe(1);
  });

  it('gives null and throws for an id it does not carry', () => {
    expect(repository.find('nope')).toBeNull();
    expect(repository.has('nope')).toBe(false);
    expect(() => repository.require('nope')).toThrow(NotFoundError);
    expect(() => repository.require('nope')).toThrow(/no thing with id nope/);
  });

  it('refuses to add over an id that is taken', () => {
    repository.add(thing('a'));
    expect(() => repository.add(thing('a', 'again'))).toThrow(/already there/);
    expect(repository.require('a').name).toBe('a');
  });

  it('replaces on put', () => {
    repository.put(thing('a'));
    repository.put(thing('a', 'again'));
    expect(repository.count()).toBe(1);
    expect(repository.require('a').name).toBe('again');
  });

  it('returns the item it was handed', () => {
    const item = thing('a');
    expect(repository.add(item)).toBe(item);
    expect(repository.put(item)).toBe(item);
  });

  it('sorts by id however the items arrived', () => {
    putAll(repository, [thing('c'), thing('a'), thing('b')]);
    expect(ids(repository)).toEqual(['a', 'b', 'c']);
  });

  it('removes and says whether it removed anything', () => {
    repository.add(thing('a'));
    expect(repository.remove('a')).toBe(true);
    expect(repository.remove('a')).toBe(false);
    expect(repository.count()).toBe(0);
  });

  it('filters', () => {
    putAll(repository, [thing('a', 'keep'), thing('b', 'drop'), thing('c', 'keep')]);
    expect(repository.where((t) => t.name === 'keep').map((t) => t.id)).toEqual(['a', 'c']);
  });

  it('clears', () => {
    putAll(repository, [thing('a'), thing('b')]);
    repository.clear();
    expect(repository.count()).toBe(0);
  });

  it('reads the id through the function it was built with', () => {
    const byName = inMemoryRepository<Thing>('thing', (t) => t.name, [thing('a', 'alpha')]);
    expect(ids(byName)).toEqual(['alpha']);
    expect(byName.require('alpha').id).toBe('a');
  });
});

describe('putAll and removeAll', () => {
  it('counts what it did', () => {
    const repository = inMemoryRepository<Thing>('thing', (t) => t.id);
    expect(putAll(repository, [thing('a'), thing('b')])).toBe(2);
    expect(removeAll(repository, ['a', 'nope'])).toBe(1);
    expect(repository.count()).toBe(1);
  });
});

describe('addAll', () => {
  it('adds a clean batch', () => {
    const repository = inMemoryRepository<Thing>('thing', (t) => t.id);
    expect(addAll(repository, [thing('a'), thing('b')])).toBe(2);
    expect(ids(repository)).toEqual(['a', 'b']);
  });

  it('puts the store back the way it was when one id is taken', () => {
    const repository = inMemoryRepository<Thing>('thing', (t) => t.id, [thing('a', 'first')]);
    expect(() => addAll(repository, [thing('b'), thing('a', 'clash'), thing('c')])).toThrow(
      ConflictError,
    );
    expect(ids(repository)).toEqual(['a']);
    expect(repository.require('a').name).toBe('first');
  });

  it('leaves an empty store empty when the batch clashes with itself', () => {
    const repository = inMemoryRepository<Thing>('thing', (t) => t.id);
    expect(() => addAll(repository, [thing('a'), thing('a')])).toThrow(ConflictError);
    expect(repository.count()).toBe(0);
  });
});
