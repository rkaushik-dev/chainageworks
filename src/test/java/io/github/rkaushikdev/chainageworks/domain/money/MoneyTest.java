package io.github.rkaushikdev.chainageworks.domain.money;

import static io.github.rkaushikdev.chainageworks.domain.money.Currency.EUR;
import static io.github.rkaushikdev.chainageworks.domain.money.Currency.GBP;
import static io.github.rkaushikdev.chainageworks.domain.money.Currency.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static Money gbp(long minor) {
        return Money.of(minor, GBP);
    }

    @Nested
    class TheCurrencyTable {

        @Test
        void carriesTheThreeCurrenciesTheContractsAreWrittenIn() {
            assertThat(Money.ALL_CURRENCIES).containsExactly(GBP, EUR, USD);
        }

        @Test
        void reportsSymbolAndMinorDigits() {
            assertThat(GBP.symbol()).isEqualTo("£");
            assertThat(EUR.symbol()).isEqualTo("€");
            assertThat(USD.minorDigits()).isEqualTo(2);
            assertThat(GBP.minorUnitsPerMajor()).isEqualTo(100);
        }

        @Test
        void recognisesOnlyTheCodesItKnows() {
            assertThat(Currency.isCode("GBP")).isTrue();
            assertThat(Currency.isCode("gbp")).isFalse();
            assertThat(Currency.isCode("JPY")).isFalse();
        }
    }

    @Nested
    class Making {

        @Test
        void holdsWholeMinorUnits() {
            assertThat(Money.of(1234, GBP)).isEqualTo(new Money(1234, GBP));
        }

        @Test
        void refusesAFractionOfAPenny() {
            assertThatThrownBy(() -> Money.of(12.5, GBP)).hasMessageContaining("whole number of minor units");
            assertThatThrownBy(() -> Money.of(Double.NaN, GBP)).hasMessageContaining("finite");
        }

        @Test
        void scalesFromMajorUnits() {
            assertThat(Money.fromMajor(12.34, GBP).minor()).isEqualTo(1234);
            assertThat(Money.fromMajor(-12.345, GBP).minor()).isEqualTo(-1235);
            assertThatThrownBy(() -> Money.fromMajor(Double.NaN, GBP)).hasMessageContaining("finite");
        }

        @Test
        void readsTheMajorAmountBack() {
            assertThat(gbp(1234).major()).isEqualTo(12.34);
        }

        @Test
        void knowsZeroAndNegative() {
            assertThat(Money.zero(GBP).isZero()).isTrue();
            assertThat(gbp(-1).isNegative()).isTrue();
            assertThat(Money.zero(GBP).isNegative()).isFalse();
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void addsAndSubtracts() {
            assertThat(gbp(1000).plus(gbp(234)).minor()).isEqualTo(1234);
            assertThat(gbp(1000).minus(gbp(1234)).minor()).isEqualTo(-234);
        }

        @Test
        void refusesToMixCurrencies() {
            assertThatThrownBy(() -> gbp(1).plus(Money.of(1, EUR))).hasMessageContaining("different currencies");
            assertThatThrownBy(() -> gbp(1).minus(Money.of(1, USD))).hasMessageContaining("different currencies");
            assertThatThrownBy(() -> gbp(1).compareTo(Money.of(1, USD))).hasMessageContaining("different currencies");
        }

        @Test
        void negatesAndTakesAnAbsoluteValue() {
            assertThat(gbp(-500).negate().minor()).isEqualTo(500);
            assertThat(gbp(-500).abs().minor()).isEqualTo(500);
            assertThat(gbp(500).abs().minor()).isEqualTo(500);
        }

        @Test
        void scalesAndRoundsHalfAwayFromZero() {
            assertThat(gbp(1000).scale(0.05).minor()).isEqualTo(50);
            assertThat(gbp(101).scale(0.005).minor()).isEqualTo(1);
            assertThat(gbp(-101).scale(0.005).minor()).isEqualTo(-1);
            assertThatThrownBy(() -> gbp(1).scale(Double.NaN)).hasMessageContaining("finite");
        }

        @Test
        void comparesAndPicksExtremes() {
            Money small = gbp(1);
            Money large = gbp(2);
            assertThat(small.compareTo(large)).isNegative();
            assertThat(Money.min(small, large)).isSameAs(small);
            assertThat(Money.max(small, large)).isSameAs(large);
        }

        @Test
        void testsEqualityOnAmountAndCurrency() {
            assertThat(gbp(1)).isEqualTo(gbp(1));
            assertThat(gbp(1)).isNotEqualTo(Money.of(1, EUR));
        }

        @Test
        void sumsAListAgainstANamedCurrency() {
            assertThat(Money.sum(List.of(gbp(100), gbp(23)), GBP).minor()).isEqualTo(123);
            assertThat(Money.sum(List.of(), EUR)).isEqualTo(new Money(0, EUR));
            assertThatThrownBy(() -> Money.sum(List.of(Money.of(1, USD)), GBP))
                    .hasMessageContaining("different currencies");
        }
    }

    @Nested
    class Format {

        @Test
        void writesTheSymbolAndTwoPlacesByDefault() {
            assertThat(gbp(123_456).format()).isEqualTo("£1,234.56");
        }

        @Test
        void padsTheMinorPart() {
            assertThat(gbp(105).format()).isEqualTo("£1.05");
            assertThat(gbp(5).format()).isEqualTo("£0.05");
        }

        @Test
        void keepsTheSignOutsideTheSymbol() {
            assertThat(gbp(-105).format()).isEqualTo("-£1.05");
        }

        @Test
        void canDropTheGrouping() {
            assertThat(gbp(123_456).format(true, false)).isEqualTo("£1234.56");
        }

        @Test
        void canWriteTheCodeInsteadOfTheSymbol() {
            assertThat(Money.of(123_456, EUR).format(false, true)).isEqualTo("1,234.56 EUR");
        }

        @Test
        void groupsLongAmountsInThrees() {
            assertThat(gbp(123_456_789_00L).format()).isEqualTo("£123,456,789.00");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsAPlainAmountAgainstTheFallbackCurrency() {
            assertThat(Money.parse("1234.56", GBP)).isEqualTo(new Money(123_456, GBP));
        }

        @Test
        void takesTheCurrencyFromASymbol() {
            assertThat(Money.parse("€10.00", GBP).currency()).isEqualTo(EUR);
            assertThat(Money.parse("$10.00", GBP).currency()).isEqualTo(USD);
        }

        @Test
        void takesTheCurrencyFromATrailingCode() {
            assertThat(Money.parse("10.00 usd", GBP).currency()).isEqualTo(USD);
        }

        @Test
        void stripsThousandsSeparators() {
            assertThat(Money.parse("1,234,567.89").minor()).isEqualTo(123_456_789);
        }

        @Test
        void readsANegativeWrittenEitherWayRound() {
            assertThat(Money.parse("-£10.00").minor()).isEqualTo(-1000);
            assertThat(Money.parse("£-10.00").minor()).isEqualTo(-1000);
        }

        @Test
        void defaultsToSterlingWhenNothingSaysOtherwise() {
            assertThat(Money.parse("10.00").currency()).isEqualTo(GBP);
        }

        @Test
        void reportsAnUnknownCurrencyCode() {
            Result<Money, CwException> r = Money.tryParse("10.00 JPY", GBP);
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("unknown currency");
        }

        @Test
        void reportsSomethingThatIsNotMoneyAtAll() {
            assertThat(Money.tryParse("a lot", GBP).isOk()).isFalse();
            assertThatThrownBy(() -> Money.parse("a lot")).isInstanceOf(BadInputException.class);
        }
    }
}
