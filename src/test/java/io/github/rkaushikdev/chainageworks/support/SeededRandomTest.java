package io.github.rkaushikdev.chainageworks.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The seeded generator the property checks draw from, pinned so a seed keeps giving the same sequence. */
class SeededRandomTest {

    private static List<Long> words(SeededRandom random, int count) {
        List<Long> words = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            words.add((long) (random.next() * 0x1p32));
        }
        return words;
    }

    @Test
    void givesThePublishedXorshiftSequence() {
        // The first three outputs of xorshift32 (13, 17, 5) from 2463534242, as Marsaglia lists them.
        assertThat(words(new SeededRandom(2_463_534_242L), 3))
                .containsExactly(723_471_715L, 2_497_366_906L, 2_064_144_800L);
    }

    @Test
    void readsTheStateAsAnUnsignedWord() {
        SeededRandom random = new SeededRandom(8_301_457);
        assertThat(words(random, 3)).containsExactly(1_955_644_407L, 4_046_235_008L, 3_591_274_126L);
        assertThat(new SeededRandom(-1).next()).isEqualTo(5.913502536714077e-05);
        assertThat(new SeededRandom(0x1_0000_0005L).next()).isEqualTo(new SeededRandom(5).next());
    }

    @Test
    void startsAZeroSeedFromOne() {
        assertThat(words(new SeededRandom(0), 2)).containsExactly(270_369L, 67_634_689L);
        assertThat(new SeededRandom(0).next()).isEqualTo(new SeededRandom(1).next());
    }

    @Test
    void drawsWholeNumbersAcrossTheRangeBothEndsIncluded() {
        SeededRandom wide = new SeededRandom(8_301_457);
        assertThat(List.of(
                        wide.between(-500_000, 500_000),
                        wide.between(-500_000, 500_000),
                        wide.between(-500_000, 500_000),
                        wide.between(-500_000, 500_000),
                        wide.between(-500_000, 500_000)))
                .containsExactly(-44_666, 442_088, 336_159, -366_547, 132_260);
        SeededRandom months = new SeededRandom(20_250_612);
        assertThat(SeededRandom.sample(5, index -> months.between(1, 12))).containsExactly(4, 9, 4, 12, 6);
    }

    @Test
    void picksFromTheListAndRefusesAnEmptyOne() {
        SeededRandom random = new SeededRandom(8_301_457);
        assertThat(random.pick(List.of("a", "b", "c", "d"))).isEqualTo("b");
        assertThat(random.bool()).isFalse();
        assertThatThrownBy(() -> random.pick(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cannot pick from an empty list");
    }
}
