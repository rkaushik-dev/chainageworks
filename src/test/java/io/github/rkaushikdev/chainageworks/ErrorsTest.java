package io.github.rkaushikdev.chainageworks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ErrorsTest {

    @Nested
    class CwExceptionBehaviour {

        @Test
        void keepsTheCodeAndTheDetailsItWasGiven() {
            CwException e =
                    new CwException(ErrorCode.BAD_INPUT, "chainage runs backwards", Details.of("from", 120, "to", 40));
            assertThat(e.code()).isEqualTo(ErrorCode.BAD_INPUT);
            assertThat(e.getMessage()).isEqualTo("chainage runs backwards");
            assertThat(e.details()).isEqualTo(Map.of("from", 120L, "to", 40L));
        }

        @Test
        void namesItselfAfterTheConcreteSubclass() {
            assertThat(new BadInputException("nope").name()).isEqualTo("BadInputException");
            assertThat(new NotFoundException("package", "WP-01").name()).isEqualTo("NotFoundException");
            assertThat(new CwException(ErrorCode.INTERNAL, "nope").name()).isEqualTo("CwException");
        }

        @Test
        void defaultsDetailsToAnEmptyMapRatherThanNull() {
            assertThat(new BadInputException("nope").details()).isEqualTo(Map.of());
        }

        @Test
        void serialisesToTheWireShape() {
            ConflictException e = new ConflictException("already certified", Details.of("certificate", 7));
            assertThat(Json.stringify(e.toJson()))
                    .isEqualTo(
                            "{\"code\":\"conflict\",\"message\":\"already certified\",\"details\":{\"certificate\":7}}");
        }

        @Test
        void isARealExceptionSoStacksAndInstanceofStillWork() {
            UnsupportedException e = new UnsupportedException("lump sum bills are not measured");
            assertThat(e).isInstanceOf(RuntimeException.class).isInstanceOf(CwException.class);
            assertThat(e.getStackTrace()).isNotEmpty();
        }
    }

    @Nested
    class NotFound {

        @Test
        void buildsAMessageFromTheKindAndTheId() {
            NotFoundException e = new NotFoundException("bill item", "E421.3");
            assertThat(e.getMessage()).isEqualTo("no bill item with id E421.3");
            assertThat(e.details()).isEqualTo(Map.of("kind", "bill item", "id", "E421.3"));
        }
    }

    @Nested
    class IsCwException {

        @Test
        void acceptsOurErrorsAndRejectsEverythingElse() {
            assertThat(CwException.isCwException(new BadInputException("x"))).isTrue();
            assertThat(CwException.isCwException(new IllegalStateException("x")))
                    .isFalse();
            assertThat(CwException.isCwException("x")).isFalse();
            assertThat(CwException.isCwException(null)).isFalse();
        }
    }

    @Nested
    class From {

        @Test
        void passesOneOfOursThroughByIdentity() {
            ConflictException original = new ConflictException("locked");
            assertThat(CwException.from(original)).isSameAs(original);
        }

        @Test
        void wrapsAPlainExceptionAndRecordsItsName() {
            CwException wrapped = CwException.from(new IllegalArgumentException("not a number"));
            assertThat(wrapped.code()).isEqualTo(ErrorCode.INTERNAL);
            assertThat(wrapped.getMessage()).isEqualTo("not a number");
            assertThat(wrapped.details()).isEqualTo(Map.of("cause", "IllegalArgumentException"));
        }

        @Test
        void stringifiesAnythingThatIsNotAnExceptionAtAll() {
            CwException wrapped = CwException.from("disk went away");
            assertThat(wrapped.code()).isEqualTo(ErrorCode.INTERNAL);
            assertThat(wrapped.getMessage()).isEqualTo("disk went away");
            assertThat(wrapped.details()).isEqualTo(Map.of());
        }
    }
}
