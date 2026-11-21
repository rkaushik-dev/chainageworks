package io.github.rkaushikdev.chainageworks.store;

import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A keyed collection of one kind of record.
 *
 * <p>Deliberately thin. Everything that decides anything lives in the domain, and the store's whole
 * job is to hand the same objects back. The one thing it does enforce is that an id is unique,
 * because two work packages with the same id would be reconciled against the bill twice.
 *
 * <p>Records come back sorted by id rather than in the order they arrived. A report that lists
 * packages in insertion order changes when somebody reimports the same file, and that shows up as a
 * diff nobody can explain.
 *
 * @param <T> the record type
 */
public interface Repository<T> {

    /** An empty repository of {@code kind}, keyed by {@code idOf}. */
    static <T> Repository<T> inMemory(String kind, Function<? super T, String> idOf) {
        return new InMemoryRepository<>(kind, idOf);
    }

    /**
     * A repository of {@code kind} holding {@code seed}.
     *
     * @throws ConflictException when two seeded records share an id
     */
    static <T> Repository<T> inMemory(String kind, Function<? super T, String> idOf, List<? extends T> seed) {
        Repository<T> repository = new InMemoryRepository<>(kind, idOf);
        for (T item : seed) {
            repository.add(item);
        }
        return repository;
    }

    /** What the records are, as the error messages name them: {@code work package}. */
    String kind();

    /** How an item's key is read, so callers never have to guess the field. */
    String idOf(T item);

    /** Every record, sorted by id. */
    List<T> all();

    /** The record under {@code id}, if there is one. */
    Optional<T> find(String id);

    /**
     * The record under {@code id}.
     *
     * @throws NotFoundException when there is none
     */
    T require(String id);

    boolean has(String id);

    /** Insert, or replace what is already there. Hands the item back. */
    T put(T item);

    /**
     * Insert, refusing an id that is already taken. Hands the item back.
     *
     * @throws ConflictException when the id is taken
     */
    T add(T item);

    /** Remove the record under {@code id}, saying whether there was one. */
    boolean remove(String id);

    int count();

    void clear();

    /** The records that match, sorted by id. */
    default List<T> where(Predicate<? super T> predicate) {
        return all().stream().filter(predicate).toList();
    }

    /** Put a batch in, replacing anything already under the same ids. Answers how many went in. */
    default int putAll(List<? extends T> items) {
        for (T item : items) {
            put(item);
        }
        return items.size();
    }

    /**
     * Add a batch, refusing the whole lot if any id is taken. Answers how many went in.
     *
     * <p>All or nothing on purpose. A half applied import leaves the store in a state no file on disk
     * describes, and the next export writes that state out as if it were what somebody meant.
     *
     * @throws ConflictException when an id is taken, with the store as it was before
     */
    default int addAll(List<? extends T> items) {
        List<T> existing = all();
        try {
            for (T item : items) {
                add(item);
            }
            return items.size();
        } catch (RuntimeException refused) {
            clear();
            putAll(existing);
            throw refused;
        }
    }

    /** Remove every record under {@code ids}, answering how many were there. */
    default int removeAll(List<String> ids) {
        int removed = 0;
        for (String id : ids) {
            if (remove(id)) {
                removed += 1;
            }
        }
        return removed;
    }

    /** Every id, in order. */
    default List<String> ids() {
        return all().stream().map(this::idOf).toList();
    }
}
