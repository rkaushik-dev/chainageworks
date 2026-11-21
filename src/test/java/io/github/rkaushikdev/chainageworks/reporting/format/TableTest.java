package io.github.rkaushikdev.chainageworks.reporting.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TableTest {

    private static final List<Column> COLUMNS =
            List.of(Column.of("package"), Column.right("earned"), Column.right("percent"));

    private static final List<List<String>> ROWS =
            List.of(List.of("WP-01", "£16,152.50", "32.5%"), List.of("WP-02", "£0.00", "0%"));

    private static final TableOptions NO_RULE = TableOptions.DEFAULT.withRule(false);

    private static List<String> lines(String text) {
        return Text.split(text, "\n");
    }

    @Nested
    class RenderTable {

        @Test
        void sizesEveryColumnToItsWidestCell() {
            assertThat(lines(Table.render(COLUMNS, ROWS)))
                    .containsExactly(
                            "package      earned  percent",
                            "-------  ----------  -------",
                            "WP-01    £16,152.50    32.5%",
                            "WP-02         £0.00       0%");
        }

        @Test
        void canLeaveTheRuleOff() {
            List<String> lines = lines(Table.render(COLUMNS, ROWS, NO_RULE));
            assertThat(lines).hasSize(3);
            assertThat(lines.get(1)).contains("WP-01");
        }

        @Test
        void honoursAWiderGap() {
            String wide = Table.render(COLUMNS, ROWS, new TableOptions(false, 4));
            assertThat(lines(wide).get(0)).isEqualTo("package        earned    percent");
        }

        @Test
        void leftAlignsByDefaultAndRightAlignsOnRequest() {
            String single = Table.render(List.of(Column.of("a")), List.of(List.of("xyz"), List.of("z")), NO_RULE);
            assertThat(lines(single).get(2)).isEqualTo("z");
            String right = Table.render(
                    List.of(Column.of("a").withAlign(Align.RIGHT)), List.of(List.of("xyz"), List.of("z")), NO_RULE);
            assertThat(lines(right).get(2)).isEqualTo("  z");
        }

        @Test
        void trimsTheTrailingSpaceOffEveryLine() {
            for (String line : lines(Table.render(COLUMNS, ROWS))) {
                assertThat(line).isEqualTo(Text.trimEnd(line));
            }
        }

        @Test
        void clipsACellThatIsWiderThanTheColumnAllows() {
            String clipped = Table.render(
                    List.of(Column.of("name").withWidth(6)), List.of(List.of("a very long package name")), NO_RULE);
            assertThat(lines(clipped).get(1)).isEqualTo("a ver…");
        }

        @Test
        void clipsTheHeadingToo() {
            String clipped = Table.render(
                    List.of(Column.of("a very long heading").withWidth(4)), List.of(List.of("x")), NO_RULE);
            assertThat(lines(clipped).get(0)).isEqualTo("a v…");
        }

        @Test
        void rendersHeadingsWithNoRowsAtAll() {
            assertThat(Table.render(COLUMNS, List.of(), NO_RULE)).isEqualTo("package  earned  percent");
        }

        @Test
        void refusesATableWithNoColumns() {
            assertThatThrownBy(() -> Table.render(List.of(), List.of()))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("at least one column");
        }

        @Test
        void refusesARowWithTheWrongNumberOfCells() {
            assertThatThrownBy(() -> Table.render(COLUMNS, List.of(List.of("a", "b"))))
                    .isInstanceOf(BadInputException.class)
                    .hasMessageContaining("wrong number of cells");
        }
    }

    @Nested
    class RenderHeading {

        @Test
        void underlinesToTheWidthOfTheText() {
            assertThat(Table.heading("progress report")).isEqualTo("progress report\n===============");
        }

        @Test
        void takesADifferentRuleCharacter() {
            assertThat(Table.heading("held work", "-")).isEqualTo("held work\n---------");
        }
    }

    @Nested
    class RenderPairs {

        @Test
        void alignsTheValues() {
            assertThat(Table.pairs(List.of(Map.entry("valuation", "3"), Map.entry("cut off", "2025-03-25"))))
                    .isEqualTo("valuation  3\ncut off    2025-03-25");
        }

        @Test
        void handlesAnEmptyList() {
            assertThat(Table.pairs(List.of())).isEmpty();
        }
    }

    @Nested
    class Indent {

        @Test
        void shiftsEveryLineAndLeavesBlankLinesAlone() {
            assertThat(Table.indent("a\n\nb")).isEqualTo("  a\n\n  b");
        }

        @Test
        void takesADifferentWidth() {
            assertThat(Table.indent("a", 4)).isEqualTo("    a");
        }
    }
}
