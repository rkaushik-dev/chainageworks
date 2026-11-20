package io.github.rkaushikdev.chainageworks.domain.money;

import static io.github.rkaushikdev.chainageworks.domain.money.Currency.EUR;
import static io.github.rkaushikdev.chainageworks.domain.money.Currency.GBP;
import static io.github.rkaushikdev.chainageworks.domain.money.Currency.USD;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.KG;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.M;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.M2;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.M3;
import static io.github.rkaushikdev.chainageworks.domain.quantity.Unit.NR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateTest {

    private static Rate rate(double major, Currency currency, Unit unit) {
        return Rate.of(major, currency, unit);
    }

    private static Quantity q(double amount, Unit unit) {
        return Quantity.of(amount, unit);
    }

    @Nested
    class Making {

        @Test
        void holdsThreeMorePlacesThanMoneyDoes() {
            assertThat(rate(12.34, GBP, M3).milliMinor()).isEqualTo(1_234_000);
            assertThat(rate(0.0035, GBP, KG).milliMinor()).isEqualTo(350);
        }

        @Test
        void readsTheMajorAmountBack() {
            assertThat(rate(12.34, GBP, M3).major()).isEqualTo(12.34);
            assertThat(rate(0.0035, GBP, KG).major()).isEqualTo(0.0035);
        }

        @Test
        void refusesAFractionalRateUnitAndANonNumber() {
            assertThatThrownBy(() -> Rate.ofMilliMinor(1.5, GBP, M)).hasMessageContaining("whole number of rate units");
            assertThatThrownBy(() -> Rate.ofMilliMinor(Double.NaN, GBP, M)).hasMessageContaining("finite");
            assertThatThrownBy(() -> rate(Double.NaN, GBP, M)).hasMessageContaining("finite");
        }

        @Test
        void knowsZeroAndNegative() {
            assertThat(Rate.zero(GBP, M).isZero()).isTrue();
            assertThat(rate(-1, GBP, M).isNegative()).isTrue();
            assertThat(Rate.zero(GBP, M).isNegative()).isFalse();
        }

        @Test
        void buildsFromAMoneyFigureForOneUnit() {
            assertThat(Rate.fromMoney(Money.of(1234, GBP), M3).major()).isEqualTo(12.34);
        }
    }

    @Nested
    class Extend {

        @Test
        void multipliesRateByQuantityAndRoundsOnce() {
            Money value = rate(12.34, GBP, M3).extend(q(10.5, M3));
            assertThat(value).isEqualTo(new Money(12_957, GBP));
            assertThat(value.major()).isEqualTo(129.57);
        }

        @Test
        void keepsTheValueOfARateWithFourDecimalPlaces() {
            assertThat(rate(0.0035, GBP, KG).extend(q(1000, KG)).major()).isEqualTo(3.5);
        }

        @Test
        void wouldLoseTheWholeItemIfTheRateWereRoundedFirst() {
            Rate tiny = rate(0.004, GBP, KG);
            assertThat(tiny.extend(q(10_000, KG)).major()).isEqualTo(40);
        }

        @Test
        void roundsHalfAwayFromZeroAtThePenny() {
            assertThat(rate(0.005, GBP, NR).extend(q(1, NR)).minor()).isEqualTo(1);
            assertThat(rate(-0.005, GBP, NR).extend(q(1, NR)).minor()).isEqualTo(-1);
            assertThat(rate(0.004, GBP, NR).extend(q(1, NR)).minor()).isZero();
        }

        @Test
        void givesNothingForNothing() {
            assertThat(rate(12.34, GBP, M3).extend(q(0, M3)).minor()).isZero();
            assertThat(Rate.zero(GBP, M3).extend(q(10, M3)).minor()).isZero();
        }

        @Test
        void carriesANegativeQuantityThroughSoAnOmissionReducesTheValue() {
            assertThat(rate(10, GBP, M3).extend(q(-2, M3)).minor()).isEqualTo(-2000);
        }

        @Test
        void refusesARateWhoseUnitIsNotTheQuantityUnit() {
            assertThatThrownBy(() -> rate(1, GBP, M3).extend(q(1, M2))).hasMessageContaining("different units");
        }
    }

    @Nested
    class Scale {

        @Test
        void scalesAndRounds() {
            assertThat(rate(10, GBP, M).scale(1.05).milliMinor()).isEqualTo(1_050_000);
            assertThat(rate(0.001, GBP, M).scale(0.5).milliMinor()).isEqualTo(50);
        }

        @Test
        void refusesAFactorThatIsNotFinite() {
            assertThatThrownBy(() -> rate(1, GBP, M).scale(Double.NaN)).hasMessageContaining("finite");
        }
    }

    @Nested
    class EqualityAndCompare {

        @Test
        void comparesEveryFieldForEquality() {
            assertThat(rate(1, GBP, M)).isEqualTo(rate(1, GBP, M));
            assertThat(rate(1, GBP, M)).isNotEqualTo(rate(1, EUR, M));
            assertThat(rate(1, GBP, M)).isNotEqualTo(rate(1, GBP, M2));
        }

        @Test
        void ordersTwoRatesForTheSameItem() {
            assertThat(Rate.compare(rate(1, GBP, M), rate(2, GBP, M))).isNegative();
        }

        @Test
        void refusesToOrderAcrossCurrenciesOrUnits() {
            assertThatThrownBy(() -> Rate.compare(rate(1, GBP, M), rate(1, EUR, M)))
                    .hasMessageContaining("different currencies");
            assertThatThrownBy(() -> Rate.compare(rate(1, GBP, M), rate(1, GBP, M2)))
                    .hasMessageContaining("different units");
        }
    }

    @Nested
    class Format {

        @Test
        void writesTwoPlacesForAnOrdinaryRate() {
            assertThat(rate(12.34, GBP, M3).format()).isEqualTo("£12.34/m3");
            assertThat(rate(12, GBP, M3).format()).isEqualTo("£12.00/m3");
        }

        @Test
        void keepsThePlacesAFineRateNeeds() {
            assertThat(rate(0.0035, GBP, KG).format()).isEqualTo("£0.0035/kg");
            assertThat(rate(1.005, GBP, KG).format()).isEqualTo("£1.005/kg");
        }

        @Test
        void honoursAnExplicitPrecision() {
            assertThat(rate(12.3456, GBP, M3).format(2, true)).isEqualTo("£12.35/m3");
        }

        @Test
        void canWriteTheCodeInsteadOfTheSymbol() {
            assertThat(rate(12.34, EUR, M3).format(false)).isEqualTo("12.34 EUR/m3");
        }

        @Test
        void describesItselfInWordsForAReportLine() {
            assertThat(rate(12.34, GBP, M3).describe()).isEqualTo("12.3400 GBP per m3");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsTheSlashForm() {
            assertThat(Rate.parse("£12.34/m3")).isEqualTo(new Rate(1_234_000, GBP, M3));
        }

        @Test
        void readsTheWordForm() {
            assertThat(Rate.parse("12.34 per m3").milliMinor()).isEqualTo(1_234_000);
            assertThat(Rate.parse("12.34 PER cu m").unit()).isEqualTo(M3);
        }

        @Test
        void readsAFineRate() {
            assertThat(Rate.parse("£0.0035 per kg").milliMinor()).isEqualTo(350);
        }

        @Test
        void stripsSeparatorsAndTakesTheCurrencyFromACode() {
            Rate r = Rate.parse("1,234.56 usd / m");
            assertThat(r.currency()).isEqualTo(USD);
            assertThat(r.major()).isEqualTo(1234.56);
        }

        @Test
        void readsANegativeRate() {
            assertThat(Rate.parse("-£10.00/m").milliMinor()).isEqualTo(-1_000_000);
        }

        @Test
        void reportsAMissingUnit() {
            Result<Rate, CwException> r = Rate.tryParse("12.34", GBP);
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("amount per unit");
        }

        @Test
        void reportsAnUnknownUnitAndAnUnknownCurrency() {
            Result<Rate, CwException> badUnit = Rate.tryParse("12.34 per furlong", GBP);
            assertThat(badUnit.isOk()).isFalse();
            assertThat(badUnit.error().getMessage()).contains("unknown unit");

            Result<Rate, CwException> badCurrency = Rate.tryParse("12.34 jpy per m", GBP);
            assertThat(badCurrency.isOk()).isFalse();
            assertThat(badCurrency.error().getMessage()).contains("unknown currency");
        }

        @Test
        void reportsAnAmountThatIsNotANumber() {
            assertThat(Rate.tryParse("lots per m", GBP).isOk()).isFalse();
            assertThatThrownBy(() -> Rate.parse("lots per m")).isInstanceOf(BadInputException.class);
        }
    }

    @Nested
    class AsMoneyPerUnit {

        @Test
        void dropsTheExtraPlacesAtThePenny() {
            assertThat(rate(12.34, GBP, M3).asMoneyPerUnit().minor()).isEqualTo(1234);
            assertThat(rate(0.0035, GBP, KG).asMoneyPerUnit().minor()).isZero();
            assertThat(rate(0.006, GBP, KG).asMoneyPerUnit().minor()).isEqualTo(1);
        }
    }
}
