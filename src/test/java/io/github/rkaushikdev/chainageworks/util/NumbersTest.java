package io.github.rkaushikdev.chainageworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumbersTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "-0.0, 0",
        "3.0, 3",
        "12.5, 12.5",
        "-12.345, -12.345",
        "0.1, 0.1",
        "1e21, 1e+21",
        "1e20, 100000000000000000000",
        "0.000001, 0.000001",
        "0.0000001, 1e-7",
        "1.5e-10, 1.5e-10",
        "123456789012345680000, 123456789012345680000",
        "0.30000000000000004, 0.30000000000000004"
    })
    void writesTheShortestTextThatReadsBack(double value, String text) {
        assertThat(Numbers.format(value)).isEqualTo(text);
    }

    @Test
    void writesTheSpecialValuesByName() {
        assertThat(Numbers.format(Double.NaN)).isEqualTo("NaN");
        assertThat(Numbers.format(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
        assertThat(Numbers.format(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
        assertThat(Numbers.format(42L)).isEqualTo("42");
    }

    @ParameterizedTest
    @CsvSource({
        "1.005, 2, 1.00",
        "1.5, 0, 2",
        "2.5, 0, 3",
        "-2.5, 0, -3",
        "12.5, 2, 12.50",
        "-0.001, 2, -0.00",
        "-0.0, 2, 0.00",
        "0.0035, 4, 0.0035",
        "3.75, 1, 3.8"
    })
    void fixesPlacesOnTheExactValue(double value, int places, String text) {
        assertThat(Numbers.toFixed(value, places)).isEqualTo(text);
    }

    @Test
    void fixesPlacesOutsideThePlainRangeAsOrdinaryText() {
        assertThat(Numbers.toFixed(1e21, 2)).isEqualTo("1e+21");
        assertThat(Numbers.toFixed(Double.NaN, 2)).isEqualTo("NaN");
        assertThatThrownBy(() -> Numbers.toFixed(1, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsNumberText() {
        assertThat(Numbers.parse(" 12.5 ")).isEqualTo(12.5);
        assertThat(Numbers.parse("")).isZero();
        assertThat(Numbers.parse("  ")).isZero();
        assertThat(Numbers.parse("1e3")).isEqualTo(1000);
        assertThat(Numbers.parse(".5")).isEqualTo(0.5);
        assertThat(Numbers.parse("5.")).isEqualTo(5);
        assertThat(Numbers.parse("-Infinity")).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(Numbers.parse("Infinity")).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Numbers.parse("0x1F")).isEqualTo(31);
        assertThat(Numbers.parse("0o17")).isEqualTo(15);
        assertThat(Numbers.parse("0b101")).isEqualTo(5);
        assertThat(Numbers.parse(" 12 ")).isEqualTo(12);
    }

    @Test
    void readsAnythingElseAsNotANumber() {
        assertThat(Numbers.parse("eight")).isNaN();
        assertThat(Numbers.parse("1,000")).isNaN();
        assertThat(Numbers.parse("1d")).isNaN();
        assertThat(Numbers.parse("NaN")).isNaN();
        assertThat(Numbers.parse("-0x10")).isNaN();
    }

    @Test
    void roundsAHalfUpAndAHalfAwayFromZero() {
        assertThat(Numbers.round(2.5)).isEqualTo(3);
        assertThat(Numbers.round(-2.5)).isEqualTo(-2);
        assertThat(Numbers.round(0.49999999999999994)).isZero();
        assertThat(Numbers.round(Double.NaN)).isNaN();
        assertThat(Numbers.roundHalfAway(2.5)).isEqualTo(3);
        assertThat(Numbers.roundHalfAway(-2.5)).isEqualTo(-3);
        assertThat(Numbers.roundHalfAway(-0.4)).isZero();
    }

    @Test
    void tellsAWholeNumberFromAFraction() {
        assertThat(Numbers.isInteger(3)).isTrue();
        assertThat(Numbers.isInteger(3.5)).isFalse();
        assertThat(Numbers.isInteger(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(Numbers.isFinite(Double.NaN)).isFalse();
    }
}
