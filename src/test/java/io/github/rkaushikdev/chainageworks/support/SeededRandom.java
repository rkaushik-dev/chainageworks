package io.github.rkaushikdev.chainageworks.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A small seeded generator, so the property checks are repeatable.
 *
 * <p>Not cryptographic and not trying to be. What matters is that a failure can be reproduced from
 * the seed printed with it, which a generator seeded from the clock cannot give. The state is
 * xorshift32 with the shifts 13, 17 and 5, held as an unsigned 32-bit word, so a seed always gives
 * the same sequence.
 */
public final class SeededRandom {

    private static final double TWO_TO_THE_32 = 0x1p32;

    private int state;

    /**
     * A generator started from the low 32 bits of {@code seed}, read as an unsigned word. A seed whose
     * low 32 bits are all zero starts from one instead, because xorshift never leaves zero.
     */
    public SeededRandom(long seed) {
        int word = (int) seed;
        this.state = word == 0 ? 1 : word;
    }

    /** The next value, from zero up to but not including one: the new state over two to the 32. */
    public double next() {
        state ^= state << 13;
        state ^= state >>> 17;
        state ^= state << 5;
        return Integer.toUnsignedLong(state) / TWO_TO_THE_32;
    }

    /** A whole number from {@code low} to {@code high}, both included. */
    public int between(int low, int high) {
        return low + (int) Math.floor(next() * ((double) high - low + 1));
    }

    /**
     * One of {@code from}, each equally likely.
     *
     * @throws IllegalArgumentException for an empty list, after drawing the value it would have used
     */
    public <T> T pick(List<T> from) {
        int index = (int) Math.floor(next() * from.size());
        if (from.isEmpty()) {
            throw new IllegalArgumentException("cannot pick from an empty list");
        }
        return from.get(index);
    }

    /** True half the time. */
    public boolean bool() {
        return bool(0.5);
    }

    /** True with probability {@code chance}. */
    public boolean bool(double chance) {
        return next() < chance;
    }

    /** A list of {@code count} values, each made from its index in turn. */
    public static <T> List<T> sample(int count, IntFunction<T> make) {
        List<T> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(make.apply(index));
        }
        return values;
    }
}
