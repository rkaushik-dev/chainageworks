package io.github.rkaushikdev.chainageworks.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import org.junit.jupiter.api.Test;

class InterimTermsTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final InterimTerms TERMS = InterimTerms.withMinimum(gbp(25_000));

    @Test
    void holdsOverAMonthThatMovesLessThanTheMinimum() {
        assertThat(TERMS.holdsOver(gbp(24_999.99))).isTrue();
        assertThat(TERMS.holdsOver(gbp(0.01))).isTrue();
    }

    @Test
    void certifiesAMonthThatReachesTheMinimumExactly() {
        assertThat(TERMS.holdsOver(gbp(25_000))).isFalse();
        assertThat(TERMS.holdsOver(gbp(40_000))).isFalse();
    }

    @Test
    void neverHoldsANegativeOrEmptyMonth() {
        assertThat(TERMS.holdsOver(gbp(-500))).isFalse();
        assertThat(TERMS.holdsOver(gbp(0))).isFalse();
    }

    @Test
    void holdsNothingWithoutAMinimum() {
        assertThat(InterimTerms.NONE.holdsOver(gbp(0.01))).isFalse();
        assertThat(new InterimTerms(null).minimumCertificate()).isEmpty();
    }

    @Test
    void refusesANegativeMinimum() {
        assertThatThrownBy(() -> InterimTerms.withMinimum(gbp(-1))).hasMessageContaining("cannot be negative");
    }
}
