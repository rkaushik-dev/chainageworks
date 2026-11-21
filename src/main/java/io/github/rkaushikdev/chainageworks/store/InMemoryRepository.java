package io.github.rkaushikdev.chainageworks.store;

import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** A {@link Repository} held in a map, for one job's worth of records. */
final class InMemoryRepository<T> implements Repository<T> {

    private final String kind;
    private final Function<? super T, String> idOf;
    private final Map<String, T> items = new HashMap<>();

    InMemoryRepository(String kind, Function<? super T, String> idOf) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.idOf = Objects.requireNonNull(idOf, "idOf");
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String idOf(T item) {
        return idOf.apply(item);
    }

    @Override
    public List<T> all() {
        return items.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public Optional<T> find(String id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public T require(String id) {
        T found = items.get(id);
        if (found == null) {
            throw new NotFoundException(kind, id);
        }
        return found;
    }

    @Override
    public boolean has(String id) {
        return items.containsKey(id);
    }

    @Override
    public T put(T item) {
        items.put(idOf(item), item);
        return item;
    }

    @Override
    public T add(T item) {
        String id = idOf(item);
        if (items.containsKey(id)) {
            throw new ConflictException(
                    "a " + kind + " with that id is already there", Details.of("kind", kind, "id", id));
        }
        items.put(id, item);
        return item;
    }

    @Override
    public boolean remove(String id) {
        return items.remove(id) != null;
    }

    @Override
    public int count() {
        return items.size();
    }

    @Override
    public void clear() {
        items.clear();
    }
}
