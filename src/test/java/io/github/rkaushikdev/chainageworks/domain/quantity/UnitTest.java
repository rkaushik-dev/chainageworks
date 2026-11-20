package io.github.rkaushikdev.chainageworks.domain.quantity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnitTest {

    @Nested
    class TheUnitTable {

        @Test
        void coversTheTenUnitsTheBillCanBeWrittenIn() {
            assertThat(Unit.ALL.stream().map(Unit::code))
                    .containsExactly("m", "m2", "m3", "ha", "nr", "t", "kg", "hr", "wk", "sum");
        }

        @Test
        void givesEveryUnitADefinitionKeyedToItself() {
            for (Unit unit : Unit.ALL) {
                assertThat(Unit.fromCode(unit.code())).contains(unit);
                assertThat(unit.unitName()).isNotEmpty();
            }
        }

        @Test
        void billsANumberToWholeUnitsAndATonneToThreePlaces() {
            assertThat(Unit.NR.decimals()).isZero();
            assertThat(Unit.T.decimals()).isEqualTo(3);
            assertThat(Unit.M3.decimals()).isEqualTo(2);
        }

        @Test
        void reportsTheDimension() {
            assertThat(Unit.M.dimension()).isEqualTo(Dimension.LENGTH);
            assertThat(Unit.M2.dimension()).isEqualTo(Dimension.AREA);
            assertThat(Unit.HA.dimension()).isEqualTo(Dimension.AREA);
            assertThat(Unit.M3.dimension()).isEqualTo(Dimension.VOLUME);
            assertThat(Unit.KG.dimension()).isEqualTo(Dimension.MASS);
            assertThat(Unit.NR.dimension()).isEqualTo(Dimension.COUNT);
            assertThat(Unit.WK.dimension()).isEqualTo(Dimension.TIME);
            assertThat(Unit.SUM.dimension()).isEqualTo(Dimension.LUMP);
        }

        @Test
        void marksTheLumpSumAsTheOneUnitNothingMeasures() {
            assertThat(Unit.SUM.isMeasurable()).isFalse();
            for (Unit unit : Unit.ALL.stream().filter(u -> u != Unit.SUM).toList()) {
                assertThat(unit.isMeasurable()).isTrue();
            }
        }

        @Test
        void namesUnitsInFull() {
            assertThat(Unit.M3.unitName()).isEqualTo("cubic metre");
            assertThat(Unit.SUM.unitName()).isEqualTo("lump sum");
        }
    }

    @Nested
    class IsCode {

        @Test
        void acceptsTheCanonicalCodesOnly() {
            assertThat(Unit.isCode("m3")).isTrue();
            assertThat(Unit.isCode("M3")).isFalse();
            assertThat(Unit.isCode("cum")).isFalse();
            assertThat(Unit.isCode("toString")).isFalse();
        }
    }

    @Nested
    class Parse {

        @Test
        void takesTheCanonicalCodeStraightThrough() {
            assertThat(Unit.parse("m3")).isEqualTo(Unit.M3);
        }

        @Test
        void ignoresCaseAndOuterWhitespace() {
            assertThat(Unit.parse("  M3 ")).isEqualTo(Unit.M3);
            assertThat(Unit.parse("NR")).isEqualTo(Unit.NR);
        }

        @Test
        void readsWhatASiteSheetWritesForVolume() {
            for (String text : List.of("m³", "CUM", "cu m", "cu  m")) {
                assertThat(Unit.parse(text)).isEqualTo(Unit.M3);
            }
        }

        @Test
        void readsWhatASiteSheetWritesForArea() {
            for (String text : List.of("m²", "SQM", "sq m", "sm")) {
                assertThat(Unit.parse(text)).isEqualTo(Unit.M2);
            }
        }

        @Test
        void readsTheSeveralSpellingsOfACount() {
            for (String text : List.of("No.", "no", "NUM", "each", "EA", "Item")) {
                assertThat(Unit.parse(text)).isEqualTo(Unit.NR);
            }
        }

        @Test
        void readsLinearMetresHoweverTheyAreAbbreviated() {
            for (String text : List.of("LM", "lin m", "lin.m", "METRE", "meter")) {
                assertThat(Unit.parse(text)).isEqualTo(Unit.M);
            }
        }

        @Test
        void readsMassAndTime() {
            assertThat(Unit.parse("te")).isEqualTo(Unit.T);
            assertThat(Unit.parse("Tonnes")).isEqualTo(Unit.T);
            assertThat(Unit.parse("hours")).isEqualTo(Unit.HR);
            assertThat(Unit.parse("H")).isEqualTo(Unit.HR);
            assertThat(Unit.parse("weeks")).isEqualTo(Unit.WK);
        }

        @Test
        void readsALumpSum() {
            assertThat(Unit.parse("LS")).isEqualTo(Unit.SUM);
            assertThat(Unit.parse("lump sum")).isEqualTo(Unit.SUM);
        }

        @Test
        void refusesABlank() {
            assertThatThrownBy(() -> Unit.parse("  ")).hasMessageContaining("blank");
        }

        @Test
        void refusesAnythingItDoesNotKnowAndQuotesItBack() {
            assertThatThrownBy(() -> Unit.parse("furlong")).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> Unit.parse(" furlong "))
                    .isInstanceOfSatisfying(BadInputException.class, e -> assertThat(e.details())
                            .isEqualTo(Map.of("text", "furlong")));
        }
    }

    @Nested
    class AssertSame {

        @Test
        void passesForTheSameUnit() {
            assertThatCode(() -> Unit.assertSame(Unit.M3, Unit.M3)).doesNotThrowAnyException();
        }

        @Test
        void refusesTwoUnitsOfTheSameDimension() {
            assertThatThrownBy(() -> Unit.assertSame(Unit.T, Unit.KG)).hasMessageContaining("different units");
        }

        @Test
        void refusesTwoUnitsOfDifferentDimensions() {
            assertThatThrownBy(() -> Unit.assertSame(Unit.M, Unit.M3)).hasMessageContaining("different units");
        }
    }
}
