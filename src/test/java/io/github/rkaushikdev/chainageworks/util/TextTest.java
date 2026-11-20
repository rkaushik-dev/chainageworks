package io.github.rkaushikdev.chainageworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void trimsUnicodeWhiteSpaceIncludingTheNoBreakSpace() {
        assertThat(Text.trim("  x  ﻿")).isEqualTo("x");
        assertThat(Text.trim("\u001Cx")).isEqualTo("\u001Cx");
        assertThat(Text.trim("   ")).isEmpty();
        assertThat(Text.trimStart("  x ")).isEqualTo("x ");
        assertThat(Text.trimEnd("  x ")).isEqualTo("  x");
        assertThat(Text.isSpace(' ')).isTrue();
        assertThat(Text.isSpace('x')).isFalse();
    }

    @Test
    void padsToAWidth() {
        assertThat(Text.padStart("7", 3, '0')).isEqualTo("007");
        assertThat(Text.padStart("1234", 3, '0')).isEqualTo("1234");
        assertThat(Text.padStart("a", 3)).isEqualTo("  a");
        assertThat(Text.padEnd("a", 3)).isEqualTo("a  ");
        assertThat(Text.padEnd("abcd", 3, '-')).isEqualTo("abcd");
    }

    @Test
    void changesCaseTheSameWayEverywhere() {
        assertThat(Text.lower("TITLE")).isEqualTo("title");
        assertThat(Text.upper("title")).isEqualTo("TITLE");
    }

    @Test
    void splitsOnALiteralAndKeepsEmptyParts() {
        assertThat(Text.split("a,,b,", ",")).containsExactly("a", "", "b", "");
        assertThat(Text.split("", ",")).containsExactly("");
        assertThat(Text.split("a.b", ".")).containsExactly("a", "b");
        assertThat(Text.split("ab", "")).containsExactly("a", "b");
    }

    @Test
    void compilesPatternsWithUnicodeSpaceAndAnEndOfTextDollar() {
        Pattern spaced = Patterns.compile("^a\\s+b$");
        assertThat(spaced.matcher("a b").find()).isTrue();
        assertThat(spaced.matcher("a b\n").find()).isFalse();
        assertThat(Patterns.compile("^\\S+$").matcher("x ").find()).isFalse();
        assertThat(Patterns.compile("^[\\s,]+$").matcher(" ,").find()).isTrue();
        assertThat(Patterns.compile("^[£$]$").matcher("$").find()).isTrue();
        assertThat(Patterns.compile("^[^]x]$").matcher("y").find()).isTrue();
        assertThat(Patterns.compile("^[a[]$").matcher("[").find()).isTrue();
        assertThat(Patterns.compile("^É$", "ig").matcher("é").find()).isTrue();
        assertThatThrownBy(() -> Patterns.compile("x", "m")).isInstanceOf(IllegalArgumentException.class);
    }
}
