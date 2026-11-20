package io.github.rkaushikdev.chainageworks.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollationTest {

    private static List<String> sorted(String... values) {
        List<String> list = new ArrayList<>(List.of(values));
        list.sort(Collation.ORDER);
        return list;
    }

    @Test
    void comparesLettersWithoutRegardToCaseFirst() {
        assertThat(sorted("b", "A", "a", "B")).containsExactly("a", "A", "b", "B");
        assertThat(Collation.compare("apple", "Banana")).isNegative();
    }

    @Test
    void putsDigitsBeforeLettersAndPunctuationBeforeDigits() {
        assertThat(sorted("CCa", "CC1", "CC-1", "CC_1", "CC 1")).containsExactly("CC 1", "CC_1", "CC-1", "CC1", "CCa");
    }

    @Test
    void readsDigitsOneAtATime() {
        assertThat(sorted("CC-10", "CC-9", "CC-1")).containsExactly("CC-1", "CC-10", "CC-9");
    }

    @Test
    void putsAShorterPrefixFirstAndTiesOnlyOnTheSameText() {
        assertThat(Collation.compare("CC", "CC-1")).isNegative();
        assertThat(Collation.compare("CC-1", "CC-1")).isZero();
        assertThat(Collation.compare("é", "z")).isPositive();
    }
}
