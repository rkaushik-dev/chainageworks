package io.github.rkaushikdev.chainageworks.valuation;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdvancePaymentTest {

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final Money CONTRACT_SUM = gbp(1_000_000);
    private static final AdvancePayment ADVANCE = AdvancePayment.of(gbp(100_000), d("2025-01-15"));

    @Nested
    class Build {

        @Test
        void takesTheUsualThresholdAndRate() {
            assertThat(ADVANCE.repaymentFrom()).isEqualTo(Percent.of(10));
            assertThat(ADVANCE.repaymentRate()).isEqualTo(Percent.of(25));
        }

        @Test
        void refusesAnAdvanceOfNothing() {
            assertThatThrownBy(() -> AdvancePayment.of(gbp(0), d("2025-01-15")))
                    .hasMessageContaining("more than nothing");
        }

        @Test
        void refusesARateThatWouldNeverRepayOrATakeOfMoreThanTheCertificate() {
            assertThatThrownBy(() -> AdvancePayment.build(gbp(10), d("2025-01-15"), null, Percent.of(0)))
                    .hasMessageContaining("at most the whole certificate");
            assertThatThrownBy(() -> AdvancePayment.build(gbp(10), d("2025-01-15"), null, Percent.of(101)))
                    .hasMessageContaining("at most the whole certificate");
        }

        @Test
        void refusesAThresholdOutsideTheContractSum() {
            assertThatThrownBy(() -> AdvancePayment.build(gbp(10), d("2025-01-15"), Percent.of(120), null))
                    .hasMessageContaining("between 0 and 100");
            assertThatThrownBy(() -> AdvancePayment.build(gbp(10), d("2025-01-15"), Percent.of(-5), null))
                    .hasMessageContaining("between 0 and 100");
        }
    }

    @Nested
    class Repayment {

        @Test
        void standsOnTheAccountOnlyOnceItHasBeenPaid() {
            assertThat(ADVANCE.paidAt(d("2025-01-14")).minor()).isZero();
            assertThat(ADVANCE.paidAt(d("2025-01-15"))).isEqualTo(gbp(100_000));
        }

        @Test
        void deductsNothingUntilTheGrossPassesTheThreshold() {
            assertThat(ADVANCE.repaidAt(gbp(100_000), CONTRACT_SUM, d("2025-03-25"))
                            .minor())
                    .isZero();
            assertThat(ADVANCE.repaidAt(gbp(80_000), CONTRACT_SUM, d("2025-03-25"))
                            .minor())
                    .isZero();
        }

        @Test
        void takesTheRateOfEverythingAboveTheThreshold() {
            assertThat(ADVANCE.repaidAt(gbp(300_000), CONTRACT_SUM, d("2025-06-25")))
                    .isEqualTo(gbp(50_000));
        }

        @Test
        void neverTakesBackMoreThanWasAdvanced() {
            assertThat(ADVANCE.repaidAt(gbp(900_000), CONTRACT_SUM, d("2025-12-25")))
                    .isEqualTo(gbp(100_000));
            assertThat(ADVANCE.isRepaidAt(gbp(900_000), CONTRACT_SUM, d("2025-12-25")))
                    .isTrue();
            assertThat(ADVANCE.isRepaidAt(gbp(300_000), CONTRACT_SUM, d("2025-06-25")))
                    .isFalse();
        }

        @Test
        void deductsNothingAgainstAnAdvanceNotYetPaid() {
            assertThat(ADVANCE.repaidAt(gbp(900_000), CONTRACT_SUM, d("2025-01-10"))
                            .minor())
                    .isZero();
        }
    }
}
