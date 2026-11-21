package io.github.rkaushikdev.chainageworks.invariants;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexReading;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.PriceAdjustment;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraChargeInput;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraReason;
import io.github.rkaushikdev.chainageworks.domain.subcontract.NotifiedBasis;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentTerms;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.support.SeededRandom;
import io.github.rkaushikdev.chainageworks.valuation.CashFlow;
import io.github.rkaushikdev.chainageworks.valuation.CashFlowInput;
import io.github.rkaushikdev.chainageworks.valuation.CashPoint;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Properties of the payment, set off, cash and fluctuation rules that hold for any input, over seeded random ones. */
class CommercialInvariantsTest {

    private static final PaymentTerms TERMS = PaymentTerms.build(PaymentTerms.DEFAULT);

    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 12, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));

    private static final List<ContraReason> SOME_REASONS =
            List.of(ContraReason.ATTENDANCE, ContraReason.WELFARE, ContraReason.RECTIFICATION, ContraReason.DAMAGE);

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    @Nested
    class APaymentIsNeverNegativeAndNeverAboveTheNotifiedSum {

        @Test
        void holdsAcrossAFewHundredRandomApplicationsAndNotices() {
            SeededRandom random = new SeededRandom(20250612);
            for (int i = 0; i < 400; i += 1) {
                int gross = random.between(0, 400_000);
                int previously = random.between(0, 400_000);
                int periodNumber = random.between(1, 12);
                LocalDate appliedOn = SiteDates.addDays(d("2025-01-01"), random.between(0, 200));
                PaymentApplication application =
                        new PaymentApplication("SA-" + i, "SC-1", periodNumber, appliedOn, gbp(gross), gbp(previously));
                boolean served = random.bool(0.6);
                Optional<PaymentNotice> notice = Optional.empty();
                if (served) {
                    LocalDate servedOn = SiteDates.addDays(application.appliedOn(), random.between(25, 45));
                    notice =
                            Optional.of(new PaymentNotice(application.id(), servedOn, gbp(random.between(0, 400_000))));
                }
                PaymentTerms.Position position = TERMS.position(application, notice, Optional.empty());
                assertThat(position.payable().minor()).isGreaterThanOrEqualTo(0);
                assertThat(position.payable().minor())
                        .isLessThanOrEqualTo(position.netOfRetention().minor());
                assertThat(position.retention().minor()).isGreaterThanOrEqualTo(0);
                assertThat(position.byDefault()).isEqualTo(position.basis() == NotifiedBasis.APPLICATION_BY_DEFAULT);
            }
        }
    }

    @Nested
    class AMissedNoticeNeverLowersWhatIsPayable {

        @Test
        void holdsWheneverThePayerValuedTheWorkBelowTheApplication() {
            SeededRandom random = new SeededRandom(97531);
            for (int i = 0; i < 200; i += 1) {
                int gross = random.between(1000, 400_000);
                PaymentApplication application =
                        new PaymentApplication("SA-" + i, "SC-1", 1, d("2025-03-25"), gbp(gross), gbp(0));
                Money lower = gbp(random.between(0, gross));
                PaymentTerms.Position inTime = TERMS.position(
                        application,
                        Optional.of(new PaymentNotice(application.id(), d("2025-04-29"), lower)),
                        Optional.empty());
                PaymentTerms.Position late = TERMS.position(
                        application,
                        Optional.of(new PaymentNotice(application.id(), d("2025-04-30"), lower)),
                        Optional.empty());
                assertThat(late.payable().minor())
                        .isGreaterThanOrEqualTo(inTime.payable().minor());
            }
        }
    }

    @Nested
    class SetOffNeverTakesAPaymentBelowNothingAndNeverLosesMoney {

        @Test
        void holdsAcrossRandomChargesAgainstRandomPayments() {
            SeededRandom random = new SeededRandom(11223344);
            for (int i = 0; i < 300; i += 1) {
                int count = random.between(0, 5);
                List<ContraCharge> charges = new ArrayList<>();
                for (int n = 0; n < count; n += 1) {
                    LocalDate incurredOn = SiteDates.addDays(d("2025-01-01"), random.between(0, 60));
                    ContraReason reason = random.pick(SOME_REASONS);
                    Money amount = gbp(random.between(0, 20_000));
                    ContraChargeInput input = ContraChargeInput.of(
                                    "CC-" + i + "-" + n, "SC-1", "a charge", amount, incurredOn)
                            .withReason(reason);
                    if (random.bool(0.6)) {
                        input = input.withNotifiedOn(SiteDates.addDays(incurredOn, random.between(0, 40)));
                    }
                    charges.add(ContraCharge.build(input));
                }
                LocalDate asAt = SiteDates.addDays(d("2025-01-01"), random.between(0, 120));
                ContraCharge.Position position = ContraCharge.position(charges, "SC-1", asAt, 14, Currency.GBP);
                Money payable = gbp(random.between(0, 60_000));

                assertThat(position.deductible().minor())
                        .isLessThanOrEqualTo(position.raised().minor());
                assertThat(position.disputed().minor())
                        .isLessThanOrEqualTo(position.deductible().minor());
                assertThat(position.applyTo(payable).minor()).isGreaterThanOrEqualTo(0);
                // Whatever is paid, less whatever waits for next time, is always the
                // payment less the set off. Nothing is lost between the two.
                assertThat(position.applyTo(payable).minor()
                                - position.carriedForward(payable).minor())
                        .isEqualTo(payable.minor() - position.deductible().minor());
            }
        }

        @Test
        void neverDeductsAChargeThatWasIncurredAfterTheDate() {
            SeededRandom random = new SeededRandom(5150);
            for (int i = 0; i < 200; i += 1) {
                LocalDate incurred = SiteDates.addDays(d("2025-01-01"), random.between(0, 120));
                ContraCharge charge = ContraCharge.build(
                        ContraChargeInput.of("CC-" + i, "SC-1", "a charge", gbp(random.between(1, 5000)), incurred)
                                .withReason(ContraReason.ATTENDANCE));
                LocalDate before = SiteDates.addDays(incurred, -random.between(1, 30));
                assertThat(charge.isDeductibleAt(before, 14)).isFalse();
                assertThat(charge.isDeductibleAt(incurred, 14)).isTrue();
            }
        }
    }

    @Nested
    class TheCashForecastNeverInventsMoney {

        @Test
        void neverReceivesMoreThanHasBeenCertifiedOnASeriesThatOnlyRises() {
            SeededRandom random = new SeededRandom(60606);
            for (int i = 0; i < 200; i += 1) {
                int late = random.between(0, 3);
                List<Money> certified = new ArrayList<>();
                long running = 0;
                for (int period = 0; period < SCHEDULE.periods().size(); period += 1) {
                    running += random.between(0, 40_000);
                    certified.add(gbp(running));
                }
                List<CashPoint> points =
                        CashFlow.build(new CashFlowInput(SCHEDULE.periods(), certified, Currency.GBP, late));

                for (CashPoint point : points) {
                    assertThat(point.received().minor())
                            .isLessThanOrEqualTo(point.certified().minor());
                    assertThat(point.outstanding().minor()).isGreaterThanOrEqualTo(0);
                    assertThat(point.receipt().minor()).isGreaterThanOrEqualTo(0);
                }
            }
        }

        @Test
        void stillBalancesWhenARemeasureTakesTheCertifiedFigureBackDown() {
            SeededRandom random = new SeededRandom(70707);
            for (int i = 0; i < 200; i += 1) {
                int late = random.between(0, 3);
                List<Money> certified = new ArrayList<>();
                long running = 0;
                for (int period = 0; period < SCHEDULE.periods().size(); period += 1) {
                    running = Math.max(0, running + random.between(-20_000, 40_000));
                    certified.add(gbp(running));
                }
                List<CashPoint> points =
                        CashFlow.build(new CashFlowInput(SCHEDULE.periods(), certified, Currency.GBP, late));

                for (int index = 0; index < points.size(); index += 1) {
                    CashPoint point = points.get(index);
                    assertThat(point.outstanding().minor())
                            .isEqualTo(
                                    point.certified().minor() - point.received().minor());
                    assertThat(point.received().minor())
                            .isEqualTo(
                                    index < late
                                            ? 0
                                            : certified.get(index - late).minor());
                }
                // The last receipt lands on whatever was certified `late` periods back,
                // so a job that stops moving still ends up square.
                assertThat(points.get(points.size() - 1).received().minor())
                        .isEqualTo(certified
                                .get(SCHEDULE.periods().size() - 1 - late)
                                .minor());
            }
        }
    }

    @Nested
    class ThePriceAdjustmentMovesWithTheIndexAndNeverTouchesTheFixedPart {

        @Test
        void holdsForAnyBaseAnyMovementAndAnyFixedShare() {
            SeededRandom random = new SeededRandom(424242);
            for (int i = 0; i < 300; i += 1) {
                int base = random.between(80, 130);
                int later = random.between(80, 200);
                int fixed = random.between(0, 100);
                IndexSeries series = IndexSeries.build(
                        "IDX",
                        null,
                        List.of(
                                new IndexReading(2024, 10, base, false),
                                new IndexReading(2025, 3, later, random.bool())));
                AdjustmentTerms clause = AdjustmentTerms.build(
                        d("2024-10-25"), Percent.of(fixed), List.of(new Weighting("IDX", Percent.of(100 - fixed))));
                Money value = gbp(random.between(0, 500_000));
                PriceAdjustment adjustment = clause.adjust(List.of(series), value, d("2025-03-25"));

                assertThat(adjustment.adjustable().minor()).isLessThanOrEqualTo(value.minor());
                if (later >= base) {
                    assertThat(adjustment.amount().minor()).isGreaterThanOrEqualTo(0);
                }
                if (later <= base) {
                    assertThat(adjustment.amount().minor()).isLessThanOrEqualTo(0);
                }
                if (fixed == 100) {
                    assertThat(adjustment.amount().minor()).isZero();
                }
                assertThat(clause.adjust(List.of(series), value, d("2024-10-25"))
                                .amount()
                                .minor())
                        .isZero();
            }
        }
    }
}
