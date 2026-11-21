package io.github.rkaushikdev.chainageworks.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RetentionStateTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final Money CONTRACT_SUM = gbp(2_000_000);

    private static RetentionState at(double gross) {
        return at(gross, q -> q);
    }

    private static RetentionState at(double gross, UnaryOperator<RetentionQuery> extra) {
        return RetentionState.compute(
                extra.apply(RetentionQuery.of(gbp(gross), CONTRACT_SUM, RetentionTerms.DEFAULT, d("2025-03-31"))));
    }

    @Nested
    class TheDefaultTerms {

        @Test
        void holdsFivePercentUpToThreePercentOfTheContractSum() {
            assertThat(RetentionTerms.DEFAULT.rate().value()).isEqualTo(5);
            assertThat(RetentionTerms.DEFAULT.limit().value()).isEqualTo(3);
            assertThat(RetentionTerms.DEFAULT.releaseOnCompletion().value()).isEqualTo(50);
        }

        @Test
        void paysAcrossTheRestOfEveryValuation() {
            assertThat(RetentionTerms.DEFAULT.payableShare().value()).isEqualTo(95);
        }

        @Test
        void refusesAPercentageOutsideNoughtToAHundred() {
            assertThatThrownBy(() -> RetentionTerms.build(RetentionTerms.DEFAULT.withRate(Percent.of(-1))))
                    .hasMessageContaining("runs from 0 to 100");
            assertThatThrownBy(() -> RetentionTerms.build(RetentionTerms.DEFAULT.withLimit(Percent.of(101))))
                    .hasMessageContaining("runs from 0 to 100");
            assertThatThrownBy(
                            () -> RetentionTerms.build(RetentionTerms.DEFAULT.withReleaseOnCompletion(Percent.of(150))))
                    .hasMessageContaining("runs from 0 to 100");
        }

        @Test
        void acceptsTheDefaults() {
            assertThat(RetentionTerms.build(RetentionTerms.DEFAULT)).isSameAs(RetentionTerms.DEFAULT);
        }
    }

    @Nested
    class AccruingRetention {

        @Test
        void holdsTheRateWhileTheTotalIsUnderTheCap() {
            RetentionState state = at(400_000);
            assertThat(state.uncapped().major()).isEqualTo(20_000);
            assertThat(state.cap().major()).isEqualTo(60_000);
            assertThat(state.accrued().major()).isEqualTo(20_000);
            assertThat(state.capReached()).isFalse();
        }

        @Test
        void stopsAtTheCapHoweverFarTheWorksGet() {
            RetentionState state = at(1_800_000);
            assertThat(state.uncapped().major()).isEqualTo(90_000);
            assertThat(state.accrued().major()).isEqualTo(60_000);
            assertThat(state.capReached()).isTrue();
        }

        @Test
        void holdsExactlyTheCapAtTheCrossover() {
            RetentionState state = at(1_200_000);
            assertThat(state.accrued().major()).isEqualTo(60_000);
            assertThat(state.capReached()).isFalse();
        }

        @Test
        void holdsNothingAgainstNothing() {
            RetentionState state = at(0);
            assertThat(state.accrued().minor()).isZero();
            assertThat(state.held().minor()).isZero();
        }
    }

    @Nested
    class ReleasingRetention {

        private final LocalDate completion = d("2025-02-15");
        private final LocalDate defects = d("2026-02-15");

        @Test
        void holdsTheLotBeforeTheWorksAreTakenOver() {
            RetentionState state =
                    at(400_000, q -> q.withSubstantialCompletionOn(completion).withAsAt(d("2025-01-31")));
            assertThat(state.held().major()).isEqualTo(20_000);
            assertThat(state.releasedOnCompletion().minor()).isZero();
        }

        @Test
        void releasesHalfAtSubstantialCompletion() {
            RetentionState state = at(400_000, q -> q.withSubstantialCompletionOn(completion));
            assertThat(state.releasedOnCompletion().major()).isEqualTo(10_000);
            assertThat(state.held().major()).isEqualTo(10_000);
        }

        @Test
        void doesNotReleaseTheRestUntilTheDefectsCertificate() {
            RetentionState state = at(400_000, q -> q.withSubstantialCompletionOn(completion)
                    .withDefectsCertificateOn(defects)
                    .withAsAt(d("2025-12-31")));
            assertThat(state.held().major()).isEqualTo(10_000);
            assertThat(state.releasedOnDefects().minor()).isZero();
        }

        @Test
        void releasesEverythingOnceTheDefectsCertificateIsIssued() {
            RetentionState state = at(400_000, q -> q.withSubstantialCompletionOn(completion)
                    .withDefectsCertificateOn(defects)
                    .withAsAt(d("2026-03-31")));
            assertThat(state.releasedOnCompletion().major()).isEqualTo(10_000);
            assertThat(state.releasedOnDefects().major()).isEqualTo(10_000);
            assertThat(state.held().minor()).isZero();
            assertThat(state.outstandingRelease().minor()).isZero();
        }

        @Test
        void releasesTheWholeLotAtTheDefectsCertificateWhenNothingWasReleasedBefore() {
            RetentionState state =
                    at(400_000, q -> q.withDefectsCertificateOn(defects).withAsAt(d("2026-03-31")));
            assertThat(state.releasedOnDefects().major()).isEqualTo(20_000);
            assertThat(state.held().minor()).isZero();
        }

        @Test
        void releasesAgainstTheCappedTotalNotTheUncappedOne() {
            RetentionState state = at(1_800_000, q -> q.withSubstantialCompletionOn(completion));
            assertThat(state.releasedOnCompletion().major()).isEqualTo(30_000);
            assertThat(state.held().major()).isEqualTo(30_000);
        }
    }

    @Nested
    class EffectiveRate {

        @Test
        void isTheContractRateWhileTheCapIsClear() {
            assertThat(at(400_000).effectiveRate(gbp(400_000)).value()).isEqualTo(5);
        }

        @Test
        void fallsAwayOnceTheCapBites() {
            assertThat(at(1_800_000).effectiveRate(gbp(1_800_000)).value()).isCloseTo(3.33, byLessThan(0.005));
        }

        @Test
        void isNothingAgainstANilValuation() {
            assertThat(at(0).effectiveRate(gbp(0)).value()).isZero();
        }
    }
}
