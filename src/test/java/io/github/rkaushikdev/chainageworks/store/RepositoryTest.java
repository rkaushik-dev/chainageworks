package io.github.rkaushikdev.chainageworks.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RepositoryTest {

    private record Thing(String id, String name) {}

    private static Thing thing(String id) {
        return new Thing(id, id);
    }

    private static Thing thing(String id, String name) {
        return new Thing(id, name);
    }

    private static Repository<Thing> things() {
        return Repository.inMemory("thing", Thing::id);
    }

    @Nested
    class InMemoryRepository {

        private Repository<Thing> repository;

        @BeforeEach
        void setUp() {
            repository = things();
        }

        @Test
        void startsEmptyAndKnowsItsKind() {
            assertThat(repository.kind()).isEqualTo("thing");
            assertThat(repository.all()).isEmpty();
            assertThat(repository.count()).isZero();
        }

        @Test
        void takesASeed() {
            Repository<Thing> seeded = Repository.inMemory("thing", Thing::id, List.of(thing("b"), thing("a")));
            assertThat(seeded.ids()).containsExactly("a", "b");
        }

        @Test
        void refusesASeedWithARepeatedId() {
            assertThatThrownBy(() -> Repository.inMemory("thing", Thing::id, List.of(thing("a"), thing("a"))))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void addsAndFinds() {
            repository.add(thing("a"));
            assertThat(repository.find("a")).contains(thing("a"));
            assertThat(repository.has("a")).isTrue();
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void givesNullAndThrowsForAnIdItDoesNotCarry() {
            assertThat(repository.find("nope")).isEmpty();
            assertThat(repository.has("nope")).isFalse();
            assertThatThrownBy(() -> repository.require("nope")).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> repository.require("nope")).hasMessageContaining("no thing with id nope");
        }

        @Test
        void refusesToAddOverAnIdThatIsTaken() {
            repository.add(thing("a"));
            assertThatThrownBy(() -> repository.add(thing("a", "again"))).hasMessageContaining("already there");
            assertThat(repository.require("a").name()).isEqualTo("a");
        }

        @Test
        void replacesOnPut() {
            repository.put(thing("a"));
            repository.put(thing("a", "again"));
            assertThat(repository.count()).isEqualTo(1);
            assertThat(repository.require("a").name()).isEqualTo("again");
        }

        @Test
        void returnsTheItemItWasHanded() {
            Thing item = thing("a");
            assertThat(repository.add(item)).isSameAs(item);
            assertThat(repository.put(item)).isSameAs(item);
        }

        @Test
        void sortsByIdHoweverTheItemsArrived() {
            repository.putAll(List.of(thing("c"), thing("a"), thing("b")));
            assertThat(repository.ids()).containsExactly("a", "b", "c");
        }

        @Test
        void removesAndSaysWhetherItRemovedAnything() {
            repository.add(thing("a"));
            assertThat(repository.remove("a")).isTrue();
            assertThat(repository.remove("a")).isFalse();
            assertThat(repository.count()).isZero();
        }

        @Test
        void filters() {
            repository.putAll(List.of(thing("a", "keep"), thing("b", "drop"), thing("c", "keep")));
            assertThat(repository.where(t -> t.name().equals("keep")).stream()
                            .map(Thing::id)
                            .toList())
                    .containsExactly("a", "c");
        }

        @Test
        void clears() {
            repository.putAll(List.of(thing("a"), thing("b")));
            repository.clear();
            assertThat(repository.count()).isZero();
        }

        @Test
        void readsTheIdThroughTheFunctionItWasBuiltWith() {
            Repository<Thing> byName = Repository.inMemory("thing", Thing::name, List.of(thing("a", "alpha")));
            assertThat(byName.ids()).containsExactly("alpha");
            assertThat(byName.require("alpha").id()).isEqualTo("a");
        }
    }

    @Nested
    class PutAllAndRemoveAll {

        @Test
        void countsWhatItDid() {
            Repository<Thing> repository = things();
            assertThat(repository.putAll(List.of(thing("a"), thing("b")))).isEqualTo(2);
            assertThat(repository.removeAll(List.of("a", "nope"))).isEqualTo(1);
            assertThat(repository.count()).isEqualTo(1);
        }
    }

    @Nested
    class AddAll {

        @Test
        void addsACleanBatch() {
            Repository<Thing> repository = things();
            assertThat(repository.addAll(List.of(thing("a"), thing("b")))).isEqualTo(2);
            assertThat(repository.ids()).containsExactly("a", "b");
        }

        @Test
        void putsTheStoreBackTheWayItWasWhenOneIdIsTaken() {
            Repository<Thing> repository = Repository.inMemory("thing", Thing::id, List.of(thing("a", "first")));
            assertThatThrownBy(() -> repository.addAll(List.of(thing("b"), thing("a", "clash"), thing("c"))))
                    .isInstanceOf(ConflictException.class);
            assertThat(repository.ids()).containsExactly("a");
            assertThat(repository.require("a").name()).isEqualTo("first");
        }

        @Test
        void leavesAnEmptyStoreEmptyWhenTheBatchClashesWithItself() {
            Repository<Thing> repository = things();
            assertThatThrownBy(() -> repository.addAll(List.of(thing("a"), thing("a"))))
                    .isInstanceOf(ConflictException.class);
            assertThat(repository.count()).isZero();
        }
    }
}
