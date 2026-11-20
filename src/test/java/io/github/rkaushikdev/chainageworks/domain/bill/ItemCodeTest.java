package io.github.rkaushikdev.chainageworks.domain.bill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemCodeTest {

    private static ItemCode c(String text) {
        return ItemCode.parse(text);
    }

    @Nested
    class WorkClasses {

        @Test
        void coversTheTwentySixClassesACivilBillUses() {
            assertThat(ItemCode.WORK_CLASSES).hasSize(26);
            assertThat(ItemCode.WORK_CLASSES.get("E")).isEqualTo("earthworks");
            assertThat(ItemCode.WORK_CLASSES.get("R")).isEqualTo("roads and pavings");
        }
    }

    @Nested
    class Of {

        @Test
        void holdsAClassDivisionsAndASuffix() {
            ItemCode code = ItemCode.of("E", List.of(4, 2, 3), "1");
            assertThat(code.workClass()).isEqualTo("E");
            assertThat(code.divisions()).containsExactly(4, 2, 3);
            assertThat(code.suffix()).contains("1");
        }

        @Test
        void upperCasesTheClassAndTheSuffix() {
            ItemCode code = ItemCode.of("e", List.of(1), "12a");
            assertThat(code.workClass()).isEqualTo("E");
            assertThat(code.suffix()).contains("12A");
        }

        @Test
        void allowsABareClassLetter() {
            assertThat(ItemCode.of("E").depth()).isZero();
        }

        @Test
        void refusesALetterThatIsNotAWorkClass() {
            assertThatThrownBy(() -> ItemCode.of("1")).hasMessageContaining("work class letter");
            assertThatThrownBy(() -> ItemCode.of("AA")).hasMessageContaining("work class letter");
        }

        @Test
        void refusesMoreThanThreeDivisions() {
            assertThatThrownBy(() -> ItemCode.of("E", List.of(1, 2, 3, 4)))
                    .hasMessageContaining("at most three divisions");
            assertThat(ItemCode.MAX_DIVISIONS).isEqualTo(3);
        }

        @Test
        void refusesADivisionThatIsNotASingleDigit() {
            // Divisions are whole numbers by type, so a fractional one cannot be handed in.
            assertThatThrownBy(() -> ItemCode.of("E", List.of(10))).hasMessageContaining("single digit");
            assertThatThrownBy(() -> ItemCode.of("E", List.of(-1))).hasMessageContaining("single digit");
        }

        @Test
        void refusesASuffixWithPunctuationInIt() {
            assertThatThrownBy(() -> ItemCode.of("E", List.of(1), "a-b"))
                    .hasMessageContaining("letters and digits only");
        }

        @Test
        void namesTheClass() {
            assertThat(ItemCode.of("F").workClassName()).isEqualTo("in situ concrete");
        }
    }

    @Nested
    class Parse {

        @Test
        void readsTheTightForm() {
            assertThat(c("E423.1")).isEqualTo(new ItemCode("E", List.of(4, 2, 3), Optional.of("1")));
        }

        @Test
        void readsTheSpacedFormOffABillPage() {
            assertThat(c("E 4 2 3.1")).isEqualTo(new ItemCode("E", List.of(4, 2, 3), Optional.of("1")));
            assertThat(c(" E 4 2 3 . 1 ")).isEqualTo(new ItemCode("E", List.of(4, 2, 3), Optional.of("1")));
        }

        @Test
        void readsACodeWithNoSuffixAndACodeWithNoDivisions() {
            assertThat(c("E423").suffix()).isEmpty();
            assertThat(c("E").divisions()).isEmpty();
        }

        @Test
        void readsALowerCaseCode() {
            assertThat(c("e423.1").workClass()).isEqualTo("E");
        }

        @Test
        void refusesMoreThanThreeDigits() {
            Result<ItemCode, CwException> r = ItemCode.tryParse("E4234");
            assertThat(r.isOk()).isFalse();
            assertThat(r.error().getMessage()).contains("not a bill item code");
        }

        @Test
        void refusesACodeThatDoesNotStartWithALetter() {
            assertThatThrownBy(() -> ItemCode.parse("423")).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> ItemCode.parse("")).isInstanceOf(BadInputException.class);
        }

        @Test
        void reportsALetterThatIsNotAWorkClass() {
            assertThat(ItemCode.tryParse("E423").isOk()).isTrue();
        }
    }

    @Nested
    class Format {

        @Test
        void writesTheTightFormByDefault() {
            assertThat(c("E423.1").format()).isEqualTo("E423.1");
            assertThat(c("E423").format()).isEqualTo("E423");
            assertThat(c("E").format()).isEqualTo("E");
        }

        @Test
        void writesTheSpacedFormOnRequest() {
            assertThat(c("E423.1").format(true)).isEqualTo("E 4 2 3.1");
            assertThat(c("E").format(true)).isEqualTo("E");
        }

        @Test
        void roundTripsThroughTheParser() {
            for (String text : List.of("E423.1", "E423", "E4", "E", "R712.14", "I185.2A")) {
                assertThat(c(text).format()).isEqualTo(text);
            }
        }
    }

    @Nested
    class Parent {

        @Test
        void dropsTheSuffixFirst() {
            assertThat(c("E423.1").parent().orElseThrow().format()).isEqualTo("E423");
        }

        @Test
        void thenDropsADivisionAtATime() {
            assertThat(c("E423").parent().orElseThrow().format()).isEqualTo("E42");
            assertThat(c("E42").parent().orElseThrow().format()).isEqualTo("E4");
            assertThat(c("E4").parent().orElseThrow().format()).isEqualTo("E");
        }

        @Test
        void givesNothingAboveTheClassLetter() {
            assertThat(c("E").parent()).isEmpty();
        }
    }

    @Nested
    class IsAncestorOf {

        @Test
        void readsAHeadingAsTheAncestorOfWhatSitsUnderIt() {
            assertThat(c("E").isAncestorOf(c("E423.1"))).isTrue();
            assertThat(c("E4").isAncestorOf(c("E423"))).isTrue();
            assertThat(c("E42").isAncestorOf(c("E423"))).isTrue();
            assertThat(c("E423").isAncestorOf(c("E423.1"))).isTrue();
        }

        @Test
        void isFalseForACodeAgainstItself() {
            assertThat(c("E423").isAncestorOf(c("E423"))).isFalse();
            assertThat(c("E").isAncestorOf(c("E"))).isFalse();
        }

        @Test
        void isFalseAcrossClassesAndAcrossBranches() {
            assertThat(c("E4").isAncestorOf(c("F423"))).isFalse();
            assertThat(c("E42").isAncestorOf(c("E433"))).isFalse();
        }

        @Test
        void isFalseTheWrongWayRound() {
            assertThat(c("E423").isAncestorOf(c("E42"))).isFalse();
        }

        @Test
        void isFalseWhenTheSupposedAncestorCarriesASuffixOfItsOwn() {
            assertThat(c("E42.1").isAncestorOf(c("E423"))).isFalse();
        }
    }

    @Nested
    class Compare {

        @Test
        void ordersByClassFirst() {
            assertThat(c("E999").compareTo(c("F111"))).isNegative();
        }

        @Test
        void ordersByDivision() {
            assertThat(c("E123").compareTo(c("E132"))).isNegative();
            assertThat(c("E423").compareTo(c("E413"))).isPositive();
        }

        @Test
        void putsAHeadingBeforeTheItemsUnderIt() {
            assertThat(c("E4").compareTo(c("E42"))).isNegative();
            assertThat(c("E423").compareTo(c("E423.1"))).isNegative();
        }

        @Test
        void ordersNumericSuffixesAsNumbersNotAsText() {
            assertThat(c("E423.2").compareTo(c("E423.10"))).isNegative();
        }

        @Test
        void putsANumericSuffixBeforeALetteredOne() {
            assertThat(c("E423.2").compareTo(c("E423.A"))).isNegative();
            assertThat(c("E423.A").compareTo(c("E423.2"))).isPositive();
            assertThat(c("E423.A").compareTo(c("E423.B"))).isNegative();
        }

        @Test
        void isZeroForTheSameCode() {
            assertThat(c("E423.1").compareTo(c("E423.1"))).isZero();
        }

        @Test
        void sortsABillIntoReadingOrder() {
            List<ItemCode> codes = new ArrayList<>(List.of("E423.2", "E4", "F1", "E423.1", "E42", "E423").stream()
                    .map(ItemCodeTest::c)
                    .toList());
            codes.sort(ItemCode.BILL_ORDER);
            assertThat(codes.stream().map(ItemCode::format))
                    .containsExactly("E4", "E42", "E423", "E423.1", "E423.2", "F1");
        }
    }

    @Nested
    class EqualityAndKey {

        @Test
        void comparesEveryPart() {
            assertThat(c("E423.1")).isEqualTo(c("E 4 2 3 . 1"));
            assertThat(c("E423.1")).isNotEqualTo(c("E423.2"));
            assertThat(c("E423")).isNotEqualTo(c("E42"));
        }

        @Test
        void buildsAKeyThatIsUniquePerCode() {
            assertThat(c("E423.1").key()).isEqualTo("E423.1");
            assertThat(c("E4").key()).isEqualTo("E4__.");
            assertThat(c("E42").key()).isNotEqualTo(c("E423").key());
        }
    }
}
