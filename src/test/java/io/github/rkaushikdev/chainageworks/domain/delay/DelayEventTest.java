package io.github.rkaushikdev.chainageworks.domain.delay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DelayEventTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final SiteCalendar CALENDAR = SiteCalendar.build(CalendarInput.of("C1", "main works"));

    private static final DelayEventInput BASE = DelayEventInput.of(
                    "DE-01",
                    "EW/2025/003",
                    DelayCause.UTILITIES,
                    "live water main found outside the recorded position",
                    d("2025-03-03"),
                    d("2025-03-14"))
            .withNotifiedOn(d("2025-03-05"))
            .withPackages(List.of("WP-01"));

    private static final DelayEvent EVENT = DelayEvent.build(BASE);

    @Nested
    class EntitlementFor {

        @Test
        void givesWeatherTimeAndNoMoney() {
            assertThat(DelayCause.WEATHER.entitlement()).isEqualTo(Entitlement.TIME_ONLY);
        }

        @Test
        void givesAnEmployerCauseBoth() {
            for (DelayCause cause : List.of(
                    DelayCause.VARIATION,
                    DelayCause.ACCESS,
                    DelayCause.DESIGN,
                    DelayCause.UTILITIES,
                    DelayCause.GROUND)) {
                assertThat(cause.entitlement()).isEqualTo(Entitlement.TIME_AND_MONEY);
            }
        }

        @Test
        void givesADelayTheContractorCausedNothing() {
            assertThat(DelayCause.CONTRACTOR.entitlement()).isEqualTo(Entitlement.NONE);
        }
    }

    @Nested
    class Build {

        @Test
        void keepsThePeriodAndThePackages() {
            assertThat(EVENT.elapsedDays()).isEqualTo(12);
            assertThat(EVENT.workingDaysLost(CALENDAR)).isEqualTo(10);
            assertThat(EVENT.packageIds()).containsExactly("WP-01");
            assertThat(EVENT.awardedDays()).isEmpty();
            assertThat(EVENT.isAssessed()).isFalse();
        }

        @Test
        void refusesAnEmptyDescription() {
            assertThatThrownBy(() -> DelayEvent.build(BASE.withDescription("  ")))
                    .hasMessageContaining("needs a description");
        }

        @Test
        void refusesAPeriodThatEndsBeforeItStarts() {
            assertThatThrownBy(() -> DelayEvent.build(BASE.withPeriod(d("2025-03-03"), d("2025-03-01"))))
                    .hasMessageContaining("ends on or after it starts");
        }

        @Test
        void refusesNoticeGivenBeforeTheEventBegan() {
            assertThatThrownBy(() -> DelayEvent.build(BASE.withNotifiedOn(d("2025-03-01"))))
                    .hasMessageContaining("before the event started");
        }

        @Test
        void refusesAnAwardThatIsNotAWholeNumberOfDays() {
            // An award is an Integer, so half a day cannot be handed in at all.
            assertThatThrownBy(() -> DelayEvent.build(BASE.withAwardedDays(-1))).hasMessageContaining("whole number");
        }
    }

    @Nested
    class TheTimeBar {

        @Test
        void letsAProperlyNotifiedEventThrough() {
            assertThat(EVENT.barReason()).isEmpty();
            assertThat(EVENT.isClaimable()).isTrue();
            assertThat(EVENT.carriesMoney()).isTrue();
        }

        @Test
        void barsAnEventNobodyGaveNoticeOf() {
            DelayEvent silent = DelayEvent.build(BASE.withNotifiedOn(null));
            assertThat(silent.barReason()).contains(BarReason.NOT_NOTIFIED);
            assertThat(silent.isClaimable()).isFalse();
        }

        @Test
        void barsNoticeGivenADayPastTheWindow() {
            DelayEvent late = DelayEvent.build(BASE.withNotifiedOn(d("2025-03-25")));
            assertThat(late.barReason()).contains(BarReason.NOTIFIED_LATE);
            DelayEvent justInTime = DelayEvent.build(BASE.withNotifiedOn(d("2025-03-24")));
            assertThat(justInTime.barReason()).isEmpty();
            assertThat(DelayEvent.DEFAULT_NOTICE_DAYS).isEqualTo(21);
        }

        @Test
        void honoursADifferentNoticePeriod() {
            DelayEvent late = DelayEvent.build(BASE.withNotifiedOn(d("2025-03-25")));
            assertThat(late.barReason(30)).isEmpty();
            assertThat(EVENT.barReason(1)).contains(BarReason.NOTIFIED_LATE);
        }

        @Test
        void barsACauseThatCarriesNoEntitlementHoweverGoodTheNotice() {
            DelayEvent ownFault = DelayEvent.build(BASE.withCause(DelayCause.CONTRACTOR));
            assertThat(ownFault.barReason()).contains(BarReason.NO_ENTITLEMENT);
        }

        @Test
        void givesWeatherTimeButNoMoney() {
            DelayEvent wet = DelayEvent.build(BASE.withCause(DelayCause.WEATHER));
            assertThat(wet.isClaimable()).isTrue();
            assertThat(wet.carriesMoney()).isFalse();
        }
    }

    @Nested
    class AssessedDays {

        @Test
        void takesTheEngineerAwardWhenThereIsOne() {
            DelayEvent awarded = DelayEvent.build(BASE.withAwardedDays(4));
            assertThat(awarded.assessedDays(CALENDAR)).isEqualTo(4);
            assertThat(awarded.isAssessed()).isTrue();
        }

        @Test
        void fallsBackToTheWorkingDaysTheEventRanFor() {
            assertThat(EVENT.assessedDays(CALENDAR)).isEqualTo(10);
        }

        @Test
        void isNothingForABarredEventEvenWhenDaysWereAwarded() {
            DelayEvent barred = DelayEvent.build(BASE.withNotifiedOn(null).withAwardedDays(4));
            assertThat(barred.assessedDays(CALENDAR)).isZero();
        }

        @Test
        void isNothingForAnAwardOfNought() {
            DelayEvent refused = DelayEvent.build(BASE.withAwardedDays(0));
            assertThat(refused.assessedDays(CALENDAR)).isZero();
            assertThat(refused.isAssessed()).isTrue();
        }

        @Test
        void countsFewerDaysOnACalendarWithAHolidayInTheWindow() {
            SiteCalendar easter = SiteCalendar.build(
                    CalendarInput.of("C2", "with holidays").withHolidays(List.of(d("2025-03-05"), d("2025-03-06"))));
            assertThat(EVENT.workingDaysLost(easter)).isEqualTo(8);
        }
    }

    @Nested
    class EventsTo {

        private final DelayEvent later = DelayEvent.build(BASE.withId("DE-02")
                .withPeriod(d("2025-05-01"), d("2025-05-09"))
                .withNotifiedOn(d("2025-05-02")));

        @Test
        void takesTheEventsThatHadStartedByTheDateInOrder() {
            assertThat(DelayEvent.eventsTo(List.of(later, EVENT), d("2025-03-31")).stream()
                            .map(DelayEvent::id))
                    .containsExactly("DE-01");
            assertThat(DelayEvent.eventsTo(List.of(later, EVENT), d("2025-05-31")).stream()
                            .map(DelayEvent::id))
                    .containsExactly("DE-01", "DE-02");
        }

        @Test
        void takesAnEventThatStartedOnTheDayItself() {
            assertThat(DelayEvent.eventsTo(List.of(EVENT), d("2025-03-03"))).hasSize(1);
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeARegisterLine() {
            assertThat(EVENT.describe()).isEqualTo("EW/2025/003 utilities, 2025-03-03 to 2025-03-14, claimable");
        }

        @Test
        void saysWhyItIsBarred() {
            DelayEvent silent = DelayEvent.build(BASE.withNotifiedOn(null));
            assertThat(silent.describe()).contains("not notified");
        }
    }
}
