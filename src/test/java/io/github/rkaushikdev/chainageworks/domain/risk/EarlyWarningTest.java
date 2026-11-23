package io.github.rkaushikdev.chainageworks.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EarlyWarningTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static ChainageRange range(String from, String to) {
        return ChainageRange.of(Stations.parse(from), Stations.parse(to));
    }

    private static final EarlyWarning UTILITIES = EarlyWarning.build(EarlyWarningInput.of(
                    "EW-01",
                    "unrecorded services under the roundabout",
                    RiskOwner.EMPLOYER,
                    d("2025-02-10"),
                    gbp(60_000))
            .withReference("EW/2025/001")
            .withExtent(range("12+400", "12+600"))
            .withMeetingOn(d("2025-02-18"))
            .withLikelihood(Percent.of(40))
            .withDelayDays(12));

    private static final EarlyWarning GROUND = EarlyWarning.build(EarlyWarningInput.of(
                    "EW-02",
                    "soft spots in the formation south of the bridge",
                    RiskOwner.CONTRACTOR,
                    d("2025-02-20"),
                    gbp(20_000))
            .withReference("EW/2025/002")
            .withLikelihood(Percent.of(75))
            .withDelayDays(5));

    private static final EarlyWarning DISPUTED = EarlyWarning.build(EarlyWarningInput.of(
                    "EW-03",
                    "access through the farm gate in wet weather",
                    RiskOwner.UNRESOLVED,
                    d("2025-01-05"),
                    gbp(10_000))
            .withReference("EW/2025/003")
            .withLikelihood(Percent.of(50)));

    private static final EarlyWarning SHARED = EarlyWarning.build(EarlyWarningInput.of(
                    "EW-04", "temporary works design still to be signed", RiskOwner.SHARED, d("2025-03-01"), gbp(8000))
            .withReference("EW/2025/004")
            .withMeetingOn(d("2025-03-05"))
            .withLikelihood(Percent.of(50)));

    private static final EarlyWarning CLOSED = EarlyWarning.build(EarlyWarningInput.of(
                    "EW-05", "a crane oversail that was agreed", RiskOwner.CONTRACTOR, d("2025-01-10"), gbp(30_000))
            .withReference("EW/2025/005")
            .withState(RiskState.AVOIDED)
            .withClosedOn(d("2025-02-01")));

    private static final List<EarlyWarning> ALL = List.of(UTILITIES, GROUND, DISPUTED, SHARED, CLOSED);

    private static EarlyWarningInput aRisk(LocalDate raisedOn, Money cost) {
        return EarlyWarningInput.of("EW-09", "a risk", RiskOwner.CONTRACTOR, raisedOn, cost);
    }

    @Nested
    class Build {

        @Test
        void fillsInTheReferenceTheStateAndTheLikelihood() {
            assertThat(DISPUTED.reference()).isEqualTo("EW/2025/003");
            assertThat(DISPUTED.state()).isEqualTo(RiskState.OPEN);
            assertThat(DISPUTED.delayDays()).isZero();
            assertThat(DISPUTED.extent()).isEmpty();
        }

        @Test
        void refusesAWarningThatSaysNothing() {
            assertThatThrownBy(() -> EarlyWarning.build(
                            EarlyWarningInput.of("EW-09", "  ", RiskOwner.CONTRACTOR, d("2025-01-01"), gbp(100))))
                    .hasMessageContaining("what the risk is");
        }

        @Test
        void refusesANegativeCost() {
            assertThatThrownBy(() -> EarlyWarning.build(aRisk(d("2025-01-01"), gbp(-1))))
                    .hasMessageContaining("less than nothing");
        }

        @Test
        void refusesALikelihoodOutsideNoughtToAHundred() {
            assertThatThrownBy(() ->
                            EarlyWarning.build(aRisk(d("2025-01-01"), gbp(100)).withLikelihood(Percent.of(140))))
                    .hasMessageContaining("runs from 0 to 100");
        }

        @Test
        void refusesHalfAWorkingDayOfDelay() {
            // The delay is an Integer, so half a day cannot be handed in; a negative one is the
            // case left to refuse.
            assertThatThrownBy(() ->
                            EarlyWarning.build(aRisk(d("2025-01-01"), gbp(100)).withDelayDays(-1)))
                    .hasMessageContaining("whole number of working days");
        }

        @Test
        void refusesAWarningClosedBeforeItWasRaised() {
            assertThatThrownBy(() ->
                            EarlyWarning.build(aRisk(d("2025-02-01"), gbp(100)).withClosedOn(d("2025-01-01"))))
                    .hasMessageContaining("close before it was raised");
        }
    }

    @Nested
    class IsOpenAt {

        @Test
        void isNotOpenBeforeItWasRaised() {
            assertThat(GROUND.isOpenAt(d("2025-02-19"))).isFalse();
            assertThat(GROUND.isOpenAt(d("2025-02-20"))).isTrue();
        }

        @Test
        void stopsBeingOpenOnTheDayItCloses() {
            assertThat(CLOSED.isOpenAt(d("2025-01-31"))).isTrue();
            assertThat(CLOSED.isOpenAt(d("2025-02-01"))).isFalse();
        }
    }

    @Nested
    class ExpectedCost {

        @Test
        void weightsTheCostByTheLikelihood() {
            assertThat(UTILITIES.expectedCost().major()).isEqualTo(24_000);
            assertThat(GROUND.expectedCost().major()).isEqualTo(15_000);
        }

        @Test
        void isTheWholeCostOnceTheRiskHasHappened() {
            EarlyWarning happened = EarlyWarning.build(
                    EarlyWarningInput.from(GROUND).withId("EW-08").withState(RiskState.HAPPENED));
            assertThat(happened.expectedCost().major()).isEqualTo(20_000);
        }

        @Test
        void isNothingOnceTheRiskHasBeenAvoided() {
            assertThat(CLOSED.expectedCost().minor()).isZero();
        }
    }

    @Nested
    class Quotation {

        private final EarlyWarning happened = EarlyWarning.build(
                EarlyWarningInput.from(UTILITIES).withState(RiskState.HAPPENED).withQuotation(gbp(48_500)));

        @Test
        void replacesTheEstimateOnceTheCompensationEventIsQuoted() {
            assertThat(happened.expectedCost()).isEqualTo(gbp(48_500));
            assertThat(happened.fullCost()).isEqualTo(gbp(48_500));
            assertThat(happened.costIfItHappens()).isEqualTo(gbp(60_000));
            assertThat(happened.awaitsQuotation()).isFalse();
        }

        @Test
        void carriesTheEstimateUntilAQuotationArrives() {
            EarlyWarning unquoted =
                    EarlyWarning.build(EarlyWarningInput.from(UTILITIES).withState(RiskState.HAPPENED));
            assertThat(unquoted.expectedCost()).isEqualTo(gbp(60_000));
            assertThat(unquoted.awaitsQuotation()).isTrue();
            assertThat(UTILITIES.awaitsQuotation()).isFalse();
        }

        @Test
        void refusesAQuotationForARiskThatHasNotHappened() {
            assertThatThrownBy(() ->
                            EarlyWarning.build(EarlyWarningInput.from(UTILITIES).withQuotation(gbp(1000))))
                    .hasMessageContaining("only a risk that happened");
        }

        @Test
        void refusesANegativeQuotationOrOneInAnotherCurrency() {
            EarlyWarningInput happenedInput = EarlyWarningInput.from(UTILITIES).withState(RiskState.HAPPENED);
            assertThatThrownBy(() -> EarlyWarning.build(happenedInput.withQuotation(gbp(-1))))
                    .hasMessageContaining("less than nothing");
            assertThatThrownBy(
                            () -> EarlyWarning.build(happenedInput.withQuotation(Money.fromMajor(1000, Currency.EUR))))
                    .hasMessageContaining("another currency");
        }

        @Test
        void putsTheQuotationIntoTheWorstCaseAsWellAsTheExpected() {
            EarlyWarning.Position position = EarlyWarning.position(List.of(happened), d("2025-03-31"), Currency.GBP);
            assertThat(position.expected()).isEqualTo(gbp(48_500));
            assertThat(position.worstCase()).isEqualTo(gbp(48_500));
            assertThat(position.carried().minor()).isZero();
        }
    }

    @Nested
    class MeetingOverdue {

        @Test
        void isOverdueWhenNobodyHasHeldAMeetingInsideTheWindow() {
            assertThat(DISPUTED.meetingOverdue(d("2025-03-25"))).isTrue();
        }

        @Test
        void isNotOverdueWhileTheWindowIsStillOpen() {
            assertThat(DISPUTED.meetingOverdue(d("2025-01-15"))).isFalse();
        }

        @Test
        void isOverdueWhenTheMeetingWasHeldLate() {
            EarlyWarning late = EarlyWarning.build(
                    EarlyWarningInput.from(GROUND).withId("EW-07").withMeetingOn(d("2025-03-20")));
            assertThat(late.meetingOverdue(d("2025-03-25"))).isTrue();
        }

        @Test
        void isNotOverdueWhenTheMeetingWasHeldInTime() {
            assertThat(UTILITIES.meetingOverdue(d("2025-03-25"))).isFalse();
        }
    }

    @Nested
    class RiskPosition {

        private final EarlyWarning.Position position = EarlyWarning.position(ALL, d("2025-03-25"), Currency.GBP);

        @Test
        void takesTheOpenWarningsDearestExpectedCostFirst() {
            assertThat(position.open().stream().map(EarlyWarning::id))
                    .containsExactly("EW-01", "EW-02", "EW-03", "EW-04");
        }

        @Test
        void keepsTheExpectedCostApartFromTheWorstCase() {
            assertThat(position.expected().major()).isEqualTo(48_000);
            assertThat(position.worstCase().major()).isEqualTo(98_000);
        }

        @Test
        void countsASharedRiskHalfToTheContractor() {
            assertThat(position.carried().major()).isEqualTo(17_000);
        }

        @Test
        void namesTheMeetingsNobodyHeld() {
            assertThat(position.overdueMeetings().stream().map(EarlyWarning::id))
                    .containsExactly("EW-02", "EW-03");
        }

        @Test
        void addsTheDelayDaysAcrossTheOpenRisks() {
            assertThat(position.delayDays()).isEqualTo(17);
        }

        @Test
        void isEmptyOnARegisterWithNothingOnIt() {
            EarlyWarning.Position clean = EarlyWarning.position(List.of(), d("2025-03-25"), Currency.GBP);
            assertThat(clean.open()).isEmpty();
            assertThat(clean.expected().minor()).isZero();
            assertThat(clean.delayDays()).isZero();
        }
    }

    @Nested
    class Unowned {

        @Test
        void namesTheRisksNobodyHasAgreedAnOwnerFor() {
            assertThat(EarlyWarning.position(ALL, d("2025-03-25"), Currency.GBP).unowned().stream()
                            .map(EarlyWarning::id))
                    .containsExactly("EW-03");
        }
    }

    @Nested
    class Describe {

        @Test
        void saysTheOwnerTheStateAndWhetherAMeetingWasHeld() {
            assertThat(UTILITIES.describe()).isEqualTo("EW/2025/001 employer open, met 2025-02-18");
            assertThat(GROUND.describe()).isEqualTo("EW/2025/002 contractor open, no meeting held");
        }
    }
}
