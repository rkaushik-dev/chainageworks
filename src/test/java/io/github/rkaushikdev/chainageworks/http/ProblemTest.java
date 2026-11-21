package io.github.rkaushikdev.chainageworks.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.ErrorCode;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.UnsupportedException;
import io.github.rkaushikdev.chainageworks.http.Problem.Failure;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProblemTest {

    @Nested
    class StatusFor {

        @Test
        void mapsEveryCodeTheDomainCanRaise() {
            assertThat(Problem.statusFor(ErrorCode.BAD_INPUT)).isEqualTo(400);
            assertThat(Problem.statusFor(ErrorCode.NOT_FOUND)).isEqualTo(404);
            assertThat(Problem.statusFor(ErrorCode.CONFLICT)).isEqualTo(409);
            assertThat(Problem.statusFor(ErrorCode.UNSUPPORTED)).isEqualTo(501);
            assertThat(Problem.statusFor(ErrorCode.INTERNAL)).isEqualTo(500);
        }
    }

    @Nested
    class ToFailure {

        @Test
        void carriesTheCodeTheMessageAndTheDetailsThrough() {
            Failure failure =
                    Problem.toFailure(new BadInputException("a station is written 12+340", Details.of("got", "x")));
            assertThat(failure.status()).isEqualTo(400);
            assertThat(failure.body().code()).isEqualTo(ErrorCode.BAD_INPUT);
            assertThat(failure.body().message()).isEqualTo("a station is written 12+340");
            assertThat(failure.body().details()).isEqualTo(Map.of("got", "x"));
        }

        @Test
        void givesANotFoundErrorItsOwnStatus() {
            Failure failure = Problem.toFailure(new NotFoundException("work package", "WP-99"));
            assertThat(failure.status()).isEqualTo(404);
            assertThat(failure.body().message()).contains("WP-99");
        }

        @Test
        void givesAConflictItsOwnStatus() {
            assertThat(Problem.toFailure(new ConflictException("two records claim the same id"))
                            .status())
                    .isEqualTo(409);
        }

        @Test
        void givesAnUnsupportedRequestItsOwnStatus() {
            assertThat(Problem.toFailure(new UnsupportedException("that unit cannot be measured"))
                            .status())
                    .isEqualTo(501);
        }

        @Test
        void turnsAnOrdinaryErrorIntoAnInternalFailure() {
            Failure failure = Problem.toFailure(new IllegalStateException("something fell over"));
            assertThat(failure.status()).isEqualTo(500);
            assertThat(failure.body().code()).isEqualTo(ErrorCode.INTERNAL);
        }

        @Test
        void turnsAThrownStringIntoAnInternalFailureRatherThanCrashing() {
            Failure failure = Problem.toFailure("just a string");
            assertThat(failure.status()).isEqualTo(500);
            assertThat(failure.body().code()).isEqualTo(ErrorCode.INTERNAL);
        }

        @Test
        void leavesADomainErrorThatIsAlreadyOneAlone() {
            CwException original = new CwException(ErrorCode.CONFLICT, "already there", Details.of("id", "WP-01"));
            assertThat(Problem.toFailure(original).body().toJson()).isEqualTo(original.toJson());
        }
    }

    @Nested
    class IsClientFailure {

        @Test
        void isTrueForEverythingTheCallerCanFix() {
            assertThat(Problem.toFailure(new BadInputException("bad")).isClientFailure())
                    .isTrue();
            assertThat(Problem.toFailure(new NotFoundException("package", "WP-99"))
                            .isClientFailure())
                    .isTrue();
            assertThat(Problem.toFailure(new ConflictException("clash")).isClientFailure())
                    .isTrue();
        }

        @Test
        void isFalseForEverythingTheCallerCannot() {
            assertThat(Problem.toFailure(new IllegalStateException("fell over")).isClientFailure())
                    .isFalse();
            assertThat(Problem.toFailure(new UnsupportedException("not built yet"))
                            .isClientFailure())
                    .isFalse();
        }
    }

    @Nested
    class DescribeFailure {

        @Test
        void readsAsOneLineForALog() {
            assertThat(Problem.toFailure(new NotFoundException("subcontract", "SC-99"))
                            .describe())
                    .isEqualTo("404 not_found: no subcontract with id SC-99");
        }

        @Test
        void readsTheSameWayForAnInternalFailure() {
            assertThat(Problem.toFailure(new IllegalStateException("fell over")).describe())
                    .contains("500 internal:");
        }
    }
}
