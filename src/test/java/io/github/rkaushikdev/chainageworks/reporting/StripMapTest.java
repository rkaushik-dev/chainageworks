package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.StripMap.Cell;
import io.github.rkaushikdev.chainageworks.reporting.StripMap.RenderOptions;
import io.github.rkaushikdev.chainageworks.reporting.StripMap.Row;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StripMapTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final WorkPackage WP01 = CONTEXT.register().require("WP-01");
    private static final LocalDate CUT_OFF = d("2025-03-31");
    private static final StripMap MAP = StripMap.build(CONTEXT, WP01, CUT_OFF, 100 * Stations.MM_PER_METRE);

    private static Row row(String stepId) {
        return MAP.rows().stream()
                .filter(r -> r.stepId().equals(stepId))
                .findFirst()
                .orElseThrow();
    }

    private static String glyphs(String stepId) {
        return row(stepId).cells().stream().map(c -> c.state().glyph()).collect(Collectors.joining());
    }

    @Nested
    class BuildStripMap {

        @Test
        void cutsThePackageIntoCellsOfTheLengthAskedFor() {
            assertThat(MAP.cellLength()).isEqualTo(100_000);
            assertThat(row("strip").cells()).hasSize(8);
            assertThat(row("strip").cells().get(0).extent().format()).isEqualTo("12+000.000 to 12+100.000");
        }

        @Test
        void defaultsToFiftyMetreCells() {
            StripMap fine = StripMap.build(CONTEXT, WP01, CUT_OFF);
            assertThat(fine.cellLength()).isEqualTo(StripMap.DEFAULT_CELL_LENGTH);
            assertThat(fine.rows().get(0).cells()).hasSize(16);
        }

        @Test
        void givesOneRowPerStepInTheRuleOfCredit() {
            assertThat(MAP.rows().stream().map(Row::stepId)).containsExactly("strip", "excavate", "place", "trim");
        }

        @Test
        void refusesACellLengthThatIsNotAPositiveWholeNumber() {
            assertThatThrownBy(() -> StripMap.build(CONTEXT, WP01, CUT_OFF, 0))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("positive whole number");
            // A cell length of 1.5 mm cannot be passed: the parameter is a long.
        }

        @Test
        void leavesAShortLastCellRatherThanRunningPastThePackage() {
            StripMap odd = StripMap.build(CONTEXT, WP01, CUT_OFF, 300 * Stations.MM_PER_METRE);
            List<Cell> cells = odd.rows().get(0).cells();
            assertThat(cells).hasSize(3);
            assertThat(Stations.format(cells.get(2).extent().to())).isEqualTo("12+800.000");
        }
    }

    @Nested
    class TheStatesOnTheStrip {

        @Test
        void creditsTheWholeOfAStepNothingIsGating() {
            assertThat(glyphs("strip")).isEqualTo("########");
        }

        @Test
        void showsTheExcavationCreditedToTheSignOffAndHeldAfterIt() {
            assertThat(glyphs("excavate")).isEqualTo("####oo..");
        }

        @Test
        void showsThePlacingWithTheNonConformancePunchedOutOfIt() {
            assertThat(glyphs("place")).isEqualTo("#xoo....");
        }

        @Test
        void showsAStepNobodyHasStartedAsUntouched() {
            assertThat(glyphs("trim")).isEqualTo("........");
        }

        @Test
        void collectsTheCellsInAStateForAChaseList() {
            List<Cell> withheld = MAP.cellsInState(CellState.WITHHELD);
            assertThat(withheld.stream().map(Cell::describe)).containsExactly("12+100.000 to 12+200.000 withheld");
        }

        @Test
        void turnsEverythingGreenOnceTheGatesOpenAndTheReportCloses() {
            List<SignOff> widened = sampleContext().signOffs().stream()
                    .map(s -> new SignOff(
                            s.id(),
                            s.pointId(),
                            s.packageId(),
                            range("12+000", "12+800"),
                            s.signedOn(),
                            s.by(),
                            s.outcome(),
                            s.note()))
                    .toList();
            ProgressContext released =
                    sampleContext(Overrides.NONE.withSignOffs(widened).withNcrs(List.of()));
            StripMap open = StripMap.build(released, WP01, CUT_OFF, 100 * Stations.MM_PER_METRE);
            assertThat(open.rows().get(1).cells().stream()
                            .map(c -> c.state().glyph())
                            .collect(Collectors.joining()))
                    .isEqualTo("######..");
        }
    }

    @Nested
    class RenderStrip {

        @Test
        void drawsOneLinePerStepWithTheNamePadded() {
            String text = MAP.render(RenderOptions.DEFAULT.withScale(false));
            assertThat(Text.split(text, "\n"))
                    .containsExactly(
                            "strip    ########", "excavate ####oo..", "place    #xoo....", "trim     ........");
        }

        @Test
        void addsAChainageScaleUnderneathByDefault() {
            List<String> lines = Text.split(MAP.render(), "\n");
            assertThat(lines).hasSize(5);
            assertThat(lines.get(4)).contains("12+000");
            assertThat(lines.get(4)).contains("12+800");
        }

        @Test
        void honoursAWiderLabelColumn() {
            String text = MAP.render(RenderOptions.DEFAULT.withLabelWidth(12).withScale(false));
            assertThat(text.startsWith("strip        #")).isTrue();
        }
    }

    @Nested
    class StationAtCell {

        @Test
        void givesTheChainageACellStartsAt() {
            assertThat(Stations.format(MAP.stationAtCell(0))).isEqualTo("12+000.000");
            assertThat(Stations.format(MAP.stationAtCell(4))).isEqualTo("12+400.000");
        }

        @Test
        void refusesAnIndexOffTheEnd() {
            assertThatThrownBy(() -> MAP.stationAtCell(99))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("no cell at that index");
        }
    }
}
