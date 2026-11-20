package io.github.rkaushikdev.chainageworks.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CsvTest {

    private static final String SHEET =
            String.join("\n", "record,package,code,quantity", "SR-01,WP-01,E423.1,1800", "SR-02,WP-01,E423.1,1750");

    private static List<Integer> lines(Csv.Table table) {
        return table.rows().stream().map(Csv.Row::line).toList();
    }

    @Nested
    class Parse {

        @Test
        void readsTheHeadingsAndTheRows() {
            Csv.Table table = Csv.parse(SHEET);
            assertThat(table.headings()).containsExactly("record", "package", "code", "quantity");
            assertThat(table.rows()).hasSize(2);
            assertThat(table.rows().get(0).values()).containsExactly("SR-01", "WP-01", "E423.1", "1800");
        }

        @Test
        void numbersLinesFromTheTopOfTheFileCountingTheHeader() {
            assertThat(lines(Csv.parse(SHEET))).containsExactly(2, 3);
        }

        @Test
        void trimsWhitespaceAroundEveryCell() {
            Csv.Table table = Csv.parse("a, b\n 1 ,  2  ");
            assertThat(table.headings()).containsExactly("a", "b");
            assertThat(table.rows().get(0).values()).containsExactly("1", "2");
        }

        @Test
        void readsAQuotedFieldHoldingTheDelimiter() {
            assertThat(Csv.parse("a,b\n\"one, two\",three").rows().get(0).values())
                    .containsExactly("one, two", "three");
        }

        @Test
        void readsADoubledQuoteAsOneQuote() {
            assertThat(Csv.parse("a\n\"he said \"\"no\"\"\"").rows().get(0).values())
                    .containsExactly("he said \"no\"");
        }

        @Test
        void readsANewlineInsideAQuotedField() {
            Csv.Table table = Csv.parse("a,b\n\"first\nsecond\",x");
            assertThat(table.rows()).hasSize(1);
            assertThat(table.rows().get(0).values().get(0)).isEqualTo("first\nsecond");
        }

        @Test
        void readsWindowsLineEndings() {
            assertThat(Csv.parse("a,b\r\n1,2\r\n").rows().get(0).values()).containsExactly("1", "2");
        }

        @Test
        void stripsAByteOrderMark() {
            assertThat(Csv.parse("﻿a,b\n1,2").headings()).containsExactly("a", "b");
        }

        @Test
        void dropsBlankLines() {
            assertThat(lines(Csv.parse("a,b\n1,2\n\n3,4\n"))).containsExactly(2, 4);
        }

        @Test
        void keepsBlankLinesWhenAskedTo() {
            assertThat(Csv.parse("a,b\n1,2\n\n3,4\n", Csv.ReadOptions.DEFAULT.withSkipBlank(false))
                            .rows())
                    .hasSize(3);
        }

        @Test
        void canReadAFileWithNoHeader() {
            Csv.Table table = Csv.parse("1,2\n3,4", Csv.ReadOptions.DEFAULT.withHeader(false));
            assertThat(table.headings()).isEmpty();
            assertThat(lines(table)).containsExactly(1, 2);
        }

        @Test
        void honoursADifferentDelimiter() {
            assertThat(Csv.parse("a;b\n1;2", Csv.ReadOptions.DEFAULT.withDelimiter(';'))
                            .rows()
                            .get(0)
                            .values())
                    .containsExactly("1", "2");
        }

        @Test
        void refusesADelimiterThatIsNotOneCharacter() {
            assertThatThrownBy(() -> Csv.parse("a,b", "||", true, true)).hasMessageContaining("one character");
        }

        @Test
        void refusesAFileThatEndsInsideAQuote() {
            assertThatThrownBy(() -> Csv.parse("a\n\"unclosed")).hasMessageContaining("ends inside a quoted field");
        }

        @Test
        void handlesAnEmptyFile() {
            Csv.Table table = Csv.parse("");
            assertThat(table.headings()).isEmpty();
            assertThat(table.rows()).isEmpty();
        }

        @Test
        void keepsAShortRowShortRatherThanPaddingIt() {
            assertThat(Csv.parse("a,b,c\n1,2").rows().get(0).values()).containsExactly("1", "2");
        }
    }

    @Nested
    class ReaderFor {

        private final Csv.Table table = Csv.parse(SHEET);
        private final Csv.RowReader reader = table.readerFor(table.rows().get(0));

        @Test
        void readsAFieldByHeading() {
            assertThat(reader.get("code")).contains("E423.1");
            assertThat(reader.line()).isEqualTo(2);
            assertThat(reader.raw()).hasSize(4);
        }

        @Test
        void ignoresTheCaseOfTheHeading() {
            assertThat(reader.get("CODE")).contains("E423.1");
        }

        @Test
        void givesNothingForAColumnTheFileDoesNotHave() {
            assertThat(reader.get("note")).isEmpty();
        }

        @Test
        void givesNothingForABlankCell() {
            Csv.Table sparse = Csv.parse("a,b\n1,");
            assertThat(sparse.readerFor(sparse.rows().get(0)).get("b")).isEmpty();
        }

        @Test
        void givesNothingForACellTheRowIsTooShortToHave() {
            Csv.Table shortRow = Csv.parse("a,b,c\n1,2");
            assertThat(shortRow.readerFor(shortRow.rows().get(0)).get("c")).isEmpty();
        }

        @Test
        void requiresAFieldAndNamesTheLineWhenItIsMissing() {
            assertThat(reader.require("code").isOk()).isTrue();
            Result<String, CwException> bad = reader.require("note");
            assertThat(bad.isOk()).isFalse();
            assertThat(bad.error().getMessage()).isEqualTo("line 2: note is missing");
            assertThat(bad.error().details()).isEqualTo(Map.of("line", 2L, "column", "note"));
        }
    }

    @Nested
    class MissingColumns {

        @Test
        void namesTheHeadingsTheFileDoesNotCarry() {
            Csv.Table table = Csv.parse(SHEET);
            assertThat(table.missingColumns(List.of("record", "measured on"))).containsExactly("measured on");
            assertThat(table.missingColumns(List.of("RECORD"))).isEmpty();
        }
    }

    @Nested
    class Write {

        @Test
        void writesAHeaderAndTheRows() {
            assertThat(Csv.write(List.of("a", "b"), List.of(List.of("1", "2")))).isEqualTo("a,b\n1,2\n");
        }

        @Test
        void quotesACellHoldingTheDelimiterAQuoteOrANewline() {
            assertThat(Csv.write(List.of("a"), List.of(List.of("one, two")))).isEqualTo("a\n\"one, two\"\n");
            assertThat(Csv.write(List.of("a"), List.of(List.of("he said \"no\""))))
                    .isEqualTo("a\n\"he said \"\"no\"\"\"\n");
            assertThat(Csv.write(List.of("a"), List.of(List.of("first\nsecond"))))
                    .isEqualTo("a\n\"first\nsecond\"\n");
        }

        @Test
        void honoursADifferentDelimiterAndLineEnding() {
            assertThat(Csv.write(List.of("a", "b"), List.of(List.of("1", "2")), ";", "\r\n"))
                    .isEqualTo("a;b\r\n1;2\r\n");
        }

        @Test
        void roundTripsThroughTheParser() {
            String text = Csv.write(List.of("a", "b"), List.of(List.of("one, two", "he said \"no\"")));
            assertThat(Csv.parse(text).rows().get(0).values()).containsExactly("one, two", "he said \"no\"");
        }

        @Test
        void writesAHeaderOnItsOwnWhenThereAreNoRows() {
            assertThat(Csv.write(List.of("a", "b"), List.of())).isEqualTo("a,b\n");
        }
    }
}
