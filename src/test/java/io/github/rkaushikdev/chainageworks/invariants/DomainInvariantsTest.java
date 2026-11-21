package io.github.rkaushikdev.chainageworks.invariants;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Apportion;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.support.SeededRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/** Properties of the money, chainage and date primitives that hold for any input, checked over seeded random ones. */
class DomainInvariantsTest {

    private static final long SEED = 8_301_457;

    @Nested
    class ApportionmentAlwaysBalances {

        @Test
        void addsBackToTheTotalForAThousandRandomSplits() {
            SeededRandom random = new SeededRandom(SEED);
            for (int run = 0; run < 1000; run += 1) {
                int total = random.between(-500_000, 500_000);
                List<Integer> weights = SeededRandom.sample(random.between(1, 8), index -> random.between(0, 100));
                if (weights.stream().allMatch(weight -> weight == 0)) {
                    continue;
                }
                List<Long> shares = Apportion.units(total, weights);
                assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
                assertThat(shares).hasSize(weights.size());
            }
        }

        @Test
        void givesABiggerShareToABiggerWeight() {
            SeededRandom random = new SeededRandom(SEED + 1);
            for (int run = 0; run < 200; run += 1) {
                int total = random.between(1000, 100_000);
                int small = random.between(1, 40);
                int large = small + random.between(1, 40);
                List<Long> shares = Apportion.units(total, List.of(small, large));
                assertThat(shares.get(1)).isGreaterThanOrEqualTo(shares.get(0));
            }
        }
    }

    /**
     * The four checks share one generator and draw from it in turn, so they run in the order they are
     * written and each sees the same ranges every time.
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ChainageRanges {

        private final SeededRandom random = new SeededRandom(SEED + 2);

        private ChainageRange someRange() {
            long from = random.between(0, 5000) * 1000L;
            return ChainageRange.of(Stations.of(from), Stations.of(from + random.between(0, 2000) * 1000L));
        }

        private List<ChainageRange> someRanges(int count) {
            return SeededRandom.sample(count, index -> someRange());
        }

        @Test
        @Order(1)
        void neverReportsMoreCoveredLengthThanThePartsAddTo() {
            for (int run = 0; run < 500; run += 1) {
                List<ChainageRange> ranges = someRanges(random.between(1, 6));
                long naive = ranges.stream().mapToLong(ChainageRange::length).sum();
                assertThat(ChainageRange.coveredLength(ranges)).isLessThanOrEqualTo(naive);
            }
        }

        @Test
        @Order(2)
        void leavesAUnionSortedDisjointAndTheSameLengthAsItself() {
            for (int run = 0; run < 500; run += 1) {
                List<ChainageRange> merged = ChainageRange.union(someRanges(random.between(1, 6)));
                for (int i = 1; i < merged.size(); i += 1) {
                    assertThat(merged.get(i).from())
                            .isGreaterThan(merged.get(i - 1).to());
                }
                List<ChainageRange> twice = new ArrayList<>(merged);
                twice.addAll(merged);
                assertThat(ChainageRange.coveredLength(merged)).isEqualTo(ChainageRange.coveredLength(twice));
            }
        }

        @Test
        @Order(3)
        void putsBackWhatASubtractionTookOut() {
            for (int run = 0; run < 500; run += 1) {
                ChainageRange whole = someRange();
                ChainageRange cut = someRange();
                List<ChainageRange> left = whole.subtract(cut);
                List<ChainageRange> shared = ChainageRange.intersectLists(List.of(whole), List.of(cut));
                List<ChainageRange> both = new ArrayList<>(left);
                both.addAll(shared);
                assertThat(ChainageRange.coveredLength(both)).isEqualTo(whole.length());
            }
        }

        @Test
        @Order(4)
        void neverIntersectsToMoreThanEitherSide() {
            for (int run = 0; run < 500; run += 1) {
                List<ChainageRange> left = someRanges(random.between(1, 4));
                List<ChainageRange> right = someRanges(random.between(1, 4));
                long shared = ChainageRange.coveredLength(ChainageRange.intersectLists(left, right));
                assertThat(shared).isLessThanOrEqualTo(ChainageRange.coveredLength(left));
                assertThat(shared).isLessThanOrEqualTo(ChainageRange.coveredLength(right));
            }
        }
    }

    @Nested
    class MoneyAndRates {

        @Test
        void extendsMonotonicallyInTheQuantity() {
            SeededRandom random = new SeededRandom(SEED + 3);
            for (int run = 0; run < 500; run += 1) {
                Rate rate = Rate.of(random.between(1, 100_000) / 100.0, Currency.GBP, Unit.M3);
                Quantity small = Quantity.of(random.between(0, 5000) / 10.0, Unit.M3);
                Quantity large = Quantity.of(small.milli() / 1000.0 + random.between(0, 5000) / 10.0, Unit.M3);
                assertThat(rate.extend(large).minor())
                        .isGreaterThanOrEqualTo(rate.extend(small).minor());
            }
        }

        @Test
        void neverLosesMoreThanAPennyEitherWayOnAPercentageSplit() {
            SeededRandom random = new SeededRandom(SEED + 4);
            for (int run = 0; run < 500; run += 1) {
                Money total = Money.fromMajor(random.between(0, 1_000_000) / 100.0, Currency.GBP);
                Percent share = Percent.of(random.between(0, 10_000) / 100.0);
                Money taken = share.of(total);
                Money left = share.complement().of(total);
                Money drift = total.minus(Money.of(taken.minor() + left.minor(), Currency.GBP));
                assertThat(Math.abs(drift.minor())).isLessThanOrEqualTo(1);
            }
        }

        @Test
        void takesNothingAtNoughtPercentAndEverythingAtAHundred() {
            SeededRandom random = new SeededRandom(SEED + 5);
            for (int run = 0; run < 200; run += 1) {
                Money total = Money.fromMajor(random.between(0, 1_000_000) / 100.0, Currency.GBP);
                assertThat(Percent.of(0).of(total).minor()).isZero();
                assertThat(Percent.of(100).of(total).minor()).isEqualTo(total.minor());
            }
        }
    }

    @Nested
    class SiteDatesInvariants {

        @Test
        void roundTripsThroughTheTextForm() {
            SeededRandom random = new SeededRandom(SEED + 6);
            LocalDate base = SiteDates.parse("2000-01-01");
            for (int run = 0; run < 2000; run += 1) {
                LocalDate date = SiteDates.addDays(base, random.between(0, 20_000));
                assertThat(SiteDates.parse(SiteDates.format(date))).isEqualTo(date);
            }
        }

        @Test
        void stepsForwardAndBackToWhereItStarted() {
            SeededRandom random = new SeededRandom(SEED + 7);
            LocalDate base = SiteDates.parse("2025-01-01");
            for (int run = 0; run < 1000; run += 1) {
                int step = random.between(-3000, 3000);
                assertThat(SiteDates.addDays(SiteDates.addDays(base, step), -step))
                        .isEqualTo(base);
                assertThat(SiteDates.diffDays(base, SiteDates.addDays(base, step)))
                        .isEqualTo(step);
            }
        }
    }
}
