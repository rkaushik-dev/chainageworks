package io.github.rkaushikdev.chainageworks.domain.delay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompletionTermsTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static final SiteCalendar CALENDAR = SiteCalendar.build(CalendarInput.of("C1", "main works"));
    private static final Money CONTRACT_SUM = gbp(2_000_000);

    private static final CompletionTerms TERMS =
            CompletionTerms.build(d("2025-09-26"), gbp(2500), Percent.of(10), null);

    private static final DelayEvent NOTIFIED = DelayEvent.build(DelayEventInput.of(
                    "DE-01",
                    "EW/2025/003",
                    DelayCause.UTILITIES,
                    "live water main outside its recorded position",
                    d("2025-03-03"),
                    d("2025-03-14"))
            .withNotifiedOn(d("2025-03-05"))
            .withAwardedDays(8));

    private static final DelayEvent BARRED = DelayEvent.build(DelayEventInput.of(
                    "DE-02",
                    "EW/2025/004",
                    DelayCause.DESIGN,
                    "revised drainage layout issued late",
                    d("2025-04-07"),
                    d("2025-04-18"))
            .withAwardedDays(5));

    @Nested
    class Build {

        @Test
        void defaultsTheNoticePeriodToTheContractStandard() {
            assertThat(TERMS.noticeDays()).isEqualTo(21);
            assertThat(TERMS.damagesCap()).contains(Percent.of(10));
        }

        @Test
        void refusesNegativeDamagesAndANonsenseNoticePeriod() {
            assertThatThrownBy(() -> CompletionTerms.build(d("2025-09-26"), gbp(-1)))
                    .hasMessageContaining("cannot be negative");
            assertThatThrownBy(() -> CompletionTerms.build(d("2025-09-26"), gbp(1), null, -1))
                    .hasMessageContaining("whole number of days");
        }
    }

    @Nested
    class Position {

        @Test
        void leavesCompletionWhereItWasWhenNothingHasHappened() {
            CompletionTerms.Position position = TERMS.position(List.of(), CALENDAR, d("2025-03-31"));
            assertThat(SiteDates.format(position.extendedCompletion())).isEqualTo("2025-09-26");
            assertThat(position.awardedDays()).isZero();
            assertThat(position.barredDays()).isZero();
        }

        @Test
        void movesCompletionByTheWorkingDaysAwarded() {
            CompletionTerms.Position position = TERMS.position(List.of(NOTIFIED), CALENDAR, d("2025-03-31"));
            assertThat(position.awardedDays()).isEqualTo(8);
            assertThat(SiteDates.format(position.extendedCompletion())).isEqualTo("2025-10-08");
        }

        @Test
        void stepsOverWeekendsBecauseAnExtensionIsInWorkingDays() {
            CompletionTerms.Position position = TERMS.position(List.of(NOTIFIED), CALENDAR, d("2025-03-31"));
            assertThat(SiteDates.format(position.extendedCompletion())).isNotEqualTo("2025-10-04");
        }

        @Test
        void reportsTheDaysABarShutOutRatherThanDroppingThem() {
            CompletionTerms.Position position = TERMS.position(List.of(NOTIFIED, BARRED), CALENDAR, d("2025-05-31"));
            assertThat(position.awardedDays()).isEqualTo(8);
            assertThat(position.barredDays()).isEqualTo(5);
            assertThat(position.barred()).hasSize(1);
            assertThat(position.barred().get(0).reason()).isEqualTo(BarReason.NOT_NOTIFIED);
            assertThat(position.barred().get(0).event().id()).isEqualTo("DE-02");
        }

        @Test
        void leavesOutAnEventThatHasNotHappenedYet() {
            CompletionTerms.Position position = TERMS.position(List.of(NOTIFIED, BARRED), CALENDAR, d("2025-03-31"));
            assertThat(position.barredDays()).isZero();
        }

        @Test
        void takesTheWorkingDaysLostWhenABarredEventWasNeverAssessed() {
            DelayEvent unassessed = DelayEvent.build(DelayEventInput.of(
                    "DE-03",
                    "EW/2025/005",
                    DelayCause.ACCESS,
                    "no access to the north end",
                    d("2025-04-07"),
                    d("2025-04-11")));
            assertThat(TERMS.position(List.of(unassessed), CALENDAR, d("2025-05-31"))
                            .barredDays())
                    .isEqualTo(5);
        }

        @Test
        void honoursALongerNoticePeriodAgreedInTheContract() {
            DelayEvent late = DelayEvent.build(DelayEventInput.of(
                            "DE-04",
                            "EW/2025/006",
                            DelayCause.GROUND,
                            "rock at formation",
                            d("2025-03-03"),
                            d("2025-03-14"))
                    .withNotifiedOn(d("2025-03-28"))
                    .withAwardedDays(6));
            CompletionTerms generous = CompletionTerms.build(d("2025-09-26"), gbp(2500), null, 40);
            assertThat(TERMS.position(List.of(late), CALENDAR, d("2025-03-31")).awardedDays())
                    .isZero();
            assertThat(generous.position(List.of(late), CALENDAR, d("2025-03-31"))
                            .awardedDays())
                    .isEqualTo(6);
        }
    }

    @Nested
    class DaysLateAndLiquidatedDamages {

        private final CompletionTerms.Position position = TERMS.position(List.of(NOTIFIED), CALENDAR, d("2025-12-31"));

        @Test
        void isNotLateBeforeTheExtendedDate() {
            assertThat(position.daysLate(d("2025-10-08"))).isZero();
            assertThat(position.isLate(d("2025-10-08"))).isFalse();
            assertThat(TERMS.liquidatedDamages(position, CONTRACT_SUM, d("2025-10-08"))
                            .minor())
                    .isZero();
        }

        @Test
        void countsCalendarDaysPastTheExtendedDateNotWorkingDays() {
            assertThat(position.daysLate(d("2025-10-15"))).isEqualTo(7);
            assertThat(TERMS.liquidatedDamages(position, CONTRACT_SUM, d("2025-10-15"))
                            .major())
                    .isEqualTo(17_500);
        }

        @Test
        void wouldHaveBeenLaterWithoutTheExtension() {
            CompletionTerms.Position noEvents = TERMS.position(List.of(), CALENDAR, d("2025-12-31"));
            assertThat(noEvents.daysLate(d("2025-10-15"))).isGreaterThan(position.daysLate(d("2025-10-15")));
        }

        @Test
        void stopsAtTheContractCap() {
            LocalDate veryLate = d("2026-06-30");
            assertThat(TERMS.liquidatedDamages(position, CONTRACT_SUM, veryLate).major())
                    .isEqualTo(200_000);
            assertThat(TERMS.isDamagesCapped(position, CONTRACT_SUM, veryLate)).isTrue();
            assertThat(TERMS.isDamagesCapped(position, CONTRACT_SUM, d("2025-10-15")))
                    .isFalse();
        }

        @Test
        void runsOnWithoutACap() {
            CompletionTerms uncapped = CompletionTerms.build(d("2025-09-26"), gbp(2500));
            CompletionTerms.Position uncappedPosition = uncapped.position(List.of(NOTIFIED), CALENDAR, d("2025-12-31"));
            assertThat(uncapped.liquidatedDamages(uncappedPosition, CONTRACT_SUM, d("2026-06-30"))
                            .major())
                    .isGreaterThan(200_000);
            assertThat(uncapped.isDamagesCapped(uncappedPosition, CONTRACT_SUM, d("2026-06-30")))
                    .isFalse();
        }
    }

    @Nested
    class Describe {

        @Test
        void saysSoWhenNothingHasMoved() {
            CompletionTerms.Position position = TERMS.position(List.of(), CALENDAR, d("2025-03-31"));
            assertThat(position.describe()).isEqualTo("completion 2025-09-26 not moved, 0 days awarded, 0 days barred");
        }

        @Test
        void namesTheNewDateAndBothCounts() {
            CompletionTerms.Position position = TERMS.position(List.of(NOTIFIED, BARRED), CALENDAR, d("2025-05-31"));
            assertThat(position.describe())
                    .isEqualTo("completion 2025-09-26 moved to 2025-10-08, 8 days awarded, 5 days barred");
        }
    }
}
