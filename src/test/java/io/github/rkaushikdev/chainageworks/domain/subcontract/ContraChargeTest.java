package io.github.rkaushikdev.chainageworks.domain.subcontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContraChargeTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final int NOTIFY_DAYS = 14;

    private static final ContraCharge ATTENDANCE = ContraCharge.build(
            ContraChargeInput.of("CC-01", "SC-EARTH", "crane time and banksman, week 9", gbp(1800), d("2025-02-28"))
                    .withReference("CC/2025/001")
                    .withReason(ContraReason.ATTENDANCE));

    private static final ContraCharge RECTIFICATION = ContraCharge.build(ContraChargeInput.of(
                    "CC-02", "SC-EARTH", "capping relaid at 12+500 outside tolerance", gbp(4200), d("2025-03-04"))
            .withReference("CC/2025/002")
            .withReason(ContraReason.RECTIFICATION)
            .withNotifiedOn(d("2025-03-10")));

    private static final ContraCharge UNNOTIFIED = ContraCharge.build(
            ContraChargeInput.of("CC-03", "SC-EARTH", "kerb line struck by a dumper", gbp(950), d("2025-03-01"))
                    .withReference("CC/2025/003")
                    .withReason(ContraReason.DAMAGE));

    private static final ContraCharge LATE = ContraCharge.build(
            ContraChargeInput.of("CC-04", "SC-EARTH", "compound cleared after the gang left", gbp(600), d("2025-02-10"))
                    .withReason(ContraReason.CLEAN_UP)
                    .withNotifiedOn(d("2025-03-20")));

    private static final ContraCharge ELSEWHERE =
            ContraCharge.build(ContraChargeInput.of("CC-05", "SC-FENCE", "cabin share", gbp(400), d("2025-03-02"))
                    .withReason(ContraReason.WELFARE));

    private static final List<ContraCharge> ALL = List.of(ATTENDANCE, RECTIFICATION, UNNOTIFIED, LATE, ELSEWHERE);

    private static ContraCharge.Position earthPosition() {
        return ContraCharge.position(ALL, "SC-EARTH", d("2025-03-25"), NOTIFY_DAYS, Currency.GBP);
    }

    @Nested
    class Build {

        @Test
        void takesTheIdAsTheReferenceAndOtherAsTheReason() {
            ContraCharge bare =
                    ContraCharge.build(ContraChargeInput.of("CC-09", "SC-EARTH", "sundry", gbp(100), d("2025-03-01")));
            assertThat(bare.reference()).isEqualTo("CC-09");
            assertThat(bare.reason()).isEqualTo(ContraReason.OTHER);
            assertThat(bare.notifiedOn()).isEmpty();
            assertThat(bare.disputed()).isFalse();
        }

        @Test
        void refusesANegativeCharge() {
            assertThatThrownBy(() -> ContraCharge.build(
                            ContraChargeInput.of("CC-09", "SC-EARTH", "sundry", gbp(-1), d("2025-03-01"))))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void refusesAChargeThatSaysNothingAboutWhatItIsFor() {
            assertThatThrownBy(() -> ContraCharge.build(
                            ContraChargeInput.of("CC-09", "SC-EARTH", "  ", gbp(100), d("2025-03-01"))))
                    .hasMessageContaining("what it is for");
        }

        @Test
        void refusesANoticeServedBeforeTheCostWasIncurred() {
            assertThatThrownBy(() -> ContraCharge.build(
                            ContraChargeInput.of("CC-09", "SC-EARTH", "sundry", gbp(100), d("2025-03-01"))
                                    .withNotifiedOn(d("2025-02-28"))))
                    .hasMessageContaining("before it was incurred");
        }
    }

    @Nested
    class IsDeductibleAt {

        @Test
        void letsAnAttendanceChargeStandWithNoNoticeAtAll() {
            assertThat(ATTENDANCE.isDeductibleAt(d("2025-03-25"), NOTIFY_DAYS)).isTrue();
        }

        @Test
        void letsANotifiedRectificationChargeStand() {
            assertThat(RECTIFICATION.isDeductibleAt(d("2025-03-25"), NOTIFY_DAYS))
                    .isTrue();
        }

        @Test
        void refusesARectificationChargeNobodyNotified() {
            assertThat(UNNOTIFIED.isDeductibleAt(d("2025-03-25"), NOTIFY_DAYS)).isFalse();
        }

        @Test
        void refusesANoticeServedOutsideTheWindow() {
            assertThat(LATE.isDeductibleAt(d("2025-03-25"), NOTIFY_DAYS)).isFalse();
        }

        @Test
        void takesANoticeServedOnTheLastDayOfTheWindow() {
            ContraCharge onTime = ContraCharge.build(
                    ContraChargeInput.from(LATE).withId("CC-10").withNotifiedOn(d("2025-02-24")));
            assertThat(onTime.isDeductibleAt(d("2025-03-25"), NOTIFY_DAYS)).isTrue();
        }

        @Test
        void leavesAChargeOutUntilTheDayItWasIncurred() {
            assertThat(RECTIFICATION.isDeductibleAt(d("2025-03-03"), NOTIFY_DAYS))
                    .isFalse();
        }

        @Test
        void leavesAChargeOutUntilTheDayItWasNotified() {
            assertThat(RECTIFICATION.isDeductibleAt(d("2025-03-09"), NOTIFY_DAYS))
                    .isFalse();
            assertThat(RECTIFICATION.isDeductibleAt(d("2025-03-10"), NOTIFY_DAYS))
                    .isTrue();
        }
    }

    @Nested
    class Barred {

        @Test
        void namesTheChargesWhoseWindowHasShutUnanswered() {
            assertThat(ContraCharge.barred(ALL, d("2025-03-25"), NOTIFY_DAYS).stream()
                            .map(ContraCharge::id))
                    .containsExactly("CC-03", "CC-04");
        }

        @Test
        void namesNothingWhileAWindowIsStillOpen() {
            assertThat(ContraCharge.barred(List.of(UNNOTIFIED), d("2025-03-10"), NOTIFY_DAYS))
                    .isEmpty();
        }

        @Test
        void neverBarsAChargeTheOrderAlreadyCovers() {
            assertThat(ContraCharge.barred(List.of(ATTENDANCE), d("2025-06-30"), NOTIFY_DAYS))
                    .isEmpty();
        }
    }

    @Nested
    class Position {

        private final ContraCharge.Position position = earthPosition();

        @Test
        void takesOnlyTheChargesAgainstThatOrder() {
            assertThat(position.charges().stream().map(ContraCharge::id))
                    .containsExactly("CC-04", "CC-01", "CC-03", "CC-02");
        }

        @Test
        void keepsWhatWasRaisedApartFromWhatMayBeTaken() {
            assertThat(position.raised().major()).isEqualTo(7550);
            assertThat(position.deductible().major()).isEqualTo(6000);
            assertThat(position.barred().major()).isEqualTo(1550);
        }

        @Test
        void reportsNothingDisputedWhenNobodyIsArguing() {
            assertThat(position.disputed().minor()).isZero();
        }

        @Test
        void reportsTheDisputedPartOfWhatMayBeTaken() {
            ContraCharge arguing =
                    ContraCharge.build(ContraChargeInput.from(RECTIFICATION).withDisputed(true));
            ContraCharge.Position disputed =
                    ContraCharge.position(List.of(arguing), "SC-EARTH", d("2025-03-25"), NOTIFY_DAYS, Currency.GBP);
            assertThat(disputed.disputed().major()).isEqualTo(4200);
            assertThat(disputed.deductible().major()).isEqualTo(4200);
        }

        @Test
        void isEmptyOnAnOrderWithNothingChargedAgainstIt() {
            ContraCharge.Position clean =
                    ContraCharge.position(ALL, "SC-NONE", d("2025-03-25"), NOTIFY_DAYS, Currency.GBP);
            assertThat(clean.charges()).isEmpty();
            assertThat(clean.raised().minor()).isZero();
        }
    }

    @Nested
    class ApplyContra {

        private final ContraCharge.Position position = earthPosition();

        @Test
        void takesTheDeductibleChargesOffThePayment() {
            assertThat(position.applyTo(gbp(20_000)).major()).isEqualTo(14_000);
        }

        @Test
        void neverTakesAPaymentBelowNothing() {
            assertThat(position.applyTo(gbp(1000)).minor()).isZero();
        }

        @Test
        void carriesTheRestIntoTheNextPayment() {
            assertThat(position.carriedForward(gbp(1000)).major()).isEqualTo(5000);
            assertThat(position.carriedForward(gbp(20_000)).minor()).isZero();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysTheOrderTheCountAndWhatComesOff() {
            assertThat(earthPosition().describe()).isEqualTo("SC-EARTH at 2025-03-25, 4 charges, 6000 deductible");
        }
    }
}
