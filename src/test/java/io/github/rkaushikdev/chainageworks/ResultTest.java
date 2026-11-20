package io.github.rkaushikdev.chainageworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Nested
    class OkAndErr {

        @Test
        void carryTheValueOrTheErrorUnderADiscriminant() {
            Result<Integer, String> good = Result.ok(42);
            assertThat(good).isEqualTo(new Result.Ok<Integer, String>(42));

            Result<Integer, BadInputException> bad = Result.err(new BadInputException("nope"));
            assertThat(bad.isOk()).isFalse();
            assertThat(bad.error().getMessage()).isEqualTo("nope");
        }

        @Test
        void narrowsThroughTheGuards() {
            Result<String, String> good = Result.ok("yes");
            Result<String, String> bad = Result.err("no");
            assertThat(good.isOk()).isTrue();
            assertThat(good.isErr()).isFalse();
            assertThat(bad.isOk()).isFalse();
            assertThat(bad.isErr()).isTrue();
        }
    }

    @Nested
    class Map {

        @Test
        void appliesTheFunctionToAValue() {
            assertThat(Result.<Integer, String>ok(2).map(n -> n * 3)).isEqualTo(Result.ok(6));
        }

        @Test
        void leavesAnErrorUntouchedAndDoesNotCallTheFunction() {
            AtomicBoolean called = new AtomicBoolean(false);
            Result<Integer, String> out = Result.<Integer, String>err("boom").map(n -> {
                called.set(true);
                return 1;
            });
            assertThat(out).isEqualTo(Result.err("boom"));
            assertThat(called).isFalse();
        }
    }

    @Nested
    class FlatMap {

        private Result<Double, String> half(int n) {
            return n > 0 ? Result.ok(n / 2.0) : Result.err("negative");
        }

        @Test
        void chainsTwoFallibleSteps() {
            assertThat(Result.<Integer, String>ok(4).flatMap(this::half)).isEqualTo(Result.ok(2.0));
        }

        @Test
        void reportsTheInnerFailure() {
            assertThat(Result.<Integer, String>ok(-4).flatMap(this::half)).isEqualTo(Result.err("negative"));
        }

        @Test
        void shortCircuitsOnAnOuterFailure() {
            Result<Integer, String> outer = Result.err("outer");
            assertThat(outer.flatMap(n -> Result.ok(1))).isEqualTo(Result.err("outer"));
        }
    }

    @Nested
    class Unwrap {

        @Test
        void returnsTheValue() {
            assertThat(Result.unwrap(Result.<String, CwException>ok("here"))).isEqualTo("here");
        }

        @Test
        void throwsTheErrorItHolds() {
            CwException e = new CwException(ErrorCode.CONFLICT, "locked");
            assertThatThrownBy(() -> Result.unwrap(Result.<String, CwException>err(e)))
                    .isSameAs(e);
        }
    }

    @Nested
    class OrElse {

        @Test
        void prefersTheValueAndFallsBackOnFailure() {
            assertThat(Result.<Integer, String>ok(9).orElse(0)).isEqualTo(9);
            assertThat(Result.<Integer, String>err("x").orElse(0)).isZero();
        }
    }

    @Nested
    class Partition {

        @Test
        void splitsABatchAndKeepsTheOrderInsideEachHalf() {
            List<Result<Integer, String>> batch =
                    List.of(Result.ok(1), Result.err("a"), Result.ok(2), Result.err("b"), Result.ok(3));
            assertThat(Result.partition(batch)).isEqualTo(new Result.Partition<>(List.of(1, 2, 3), List.of("a", "b")));
        }

        @Test
        void handlesAnEmptyBatch() {
            assertThat(Result.partition(List.<Result<Integer, String>>of()))
                    .isEqualTo(new Result.Partition<Integer, String>(List.of(), List.of()));
        }
    }

    @Nested
    class Collect {

        @Test
        void gathersEveryValueWhenTheyAllPass() {
            List<Result<Integer, String>> all = List.of(Result.ok(1), Result.ok(2));
            assertThat(Result.collect(all)).isEqualTo(Result.ok(List.of(1, 2)));
        }

        @Test
        void reportsOnlyTheFirstFailure() {
            List<Result<Integer, String>> all = List.of(Result.ok(1), Result.err("first"), Result.err("second"));
            assertThat(Result.collect(all)).isEqualTo(Result.err("first"));
        }
    }
}
