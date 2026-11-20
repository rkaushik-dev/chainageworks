package io.github.rkaushikdev.chainageworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void writesCompactTextWithKeysInInsertionOrder() {
        ObjectNode doc = Json.object();
        doc.put("b", 1);
        doc.put("a", "x");
        doc.set("list", Json.valueOf(List.of(1, 2.5, true)));
        doc.putNull("none");
        assertThat(Json.stringify(doc)).isEqualTo("{\"b\":1,\"a\":\"x\",\"list\":[1,2.5,true],\"none\":null}");
    }

    @Test
    void indentsTwoSpacesPerLevelAndKeepsEmptyContainersShort() {
        ObjectNode doc = Json.object();
        doc.put("a", 1);
        doc.set("b", Json.valueOf(List.of(1, Map.of())));
        doc.set("c", Json.array());
        assertThat(Json.stringify(doc, 2))
                .isEqualTo("{\n  \"a\": 1,\n  \"b\": [\n    1,\n    {}\n  ],\n  \"c\": []\n}");
    }

    @Test
    void writesWholeDoublesAsWholeNumbersAndNonFiniteOnesAsNull() {
        ObjectNode doc = Json.object();
        doc.put("whole", 3.0);
        doc.put("half", 0.5);
        doc.put("nan", Double.NaN);
        doc.put("tiny", 1e-7);
        assertThat(Json.stringify(doc)).isEqualTo("{\"whole\":3,\"half\":0.5,\"nan\":null,\"tiny\":1e-7}");
        assertThat(Json.number(3.0).isIntegralNumber()).isTrue();
        assertThat(Json.number(3.5).isIntegralNumber()).isFalse();
    }

    @Test
    void leavesAbsentValuesOut() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("here", 1);
        fields.put("gone", Optional.empty());
        fields.put("int", OptionalInt.of(2));
        fields.put("noInt", OptionalInt.empty());
        fields.put("long", OptionalLong.of(3));
        fields.put("noLong", OptionalLong.empty());
        fields.put("double", OptionalDouble.of(0.5));
        fields.put("noDouble", OptionalDouble.empty());
        fields.put("some", Optional.of("x"));
        assertThat(Json.stringify(Json.valueOf(fields)))
                .isEqualTo("{\"here\":1,\"int\":2,\"long\":3,\"double\":0.5,\"some\":\"x\"}");
        assertThat(Json.stringify(Json.valueOf(Optional.empty()))).isEmpty();
        assertThat(Json.stringify(Json.valueOf(List.of(Optional.empty())))).isEqualTo("[null]");
    }

    @Test
    void escapesOnlyWhatTheGrammarRequires() {
        assertThat(Json.quote("a\"b\\c\n\t\r\b\f\u0001£\uD83D\uDE00"))
                .isEqualTo("\"a\\\"b\\\\c\\n\\t\\r\\b\\f\\u0001£\uD83D\uDE00\"");
        assertThat(Json.quote("\uD800")).isEqualTo("\"\\ud800\"");
        assertThat(Json.quote("x\uDC00")).isEqualTo("\"x\\udc00\"");
    }

    @Test
    void readsTextBackIntoATree() throws Json.JsonFailure {
        JsonNode tree = Json.parse("{\"a\": [1, 2.5, \"x\"], \"b\": null}");
        assertThat(tree.get("a").get(1).doubleValue()).isEqualTo(2.5);
        assertThat(Json.stringify(tree)).isEqualTo("{\"a\":[1,2.5,\"x\"],\"b\":null}");
    }

    @Test
    void refusesTextThatIsNotJson() {
        assertThatThrownBy(() -> Json.parse("{")).isInstanceOf(Json.JsonFailure.class);
        assertThatThrownBy(() -> Json.parse("")).isInstanceOf(Json.JsonFailure.class);
        assertThatThrownBy(() -> Json.parse("{} {}")).isInstanceOf(Json.JsonFailure.class);
    }

    @Test
    void turnsPlainValuesIntoTrees() {
        assertThat(Json.valueOf(null).isNull()).isTrue();
        assertThat(Json.valueOf(Thread.State.NEW).textValue()).isEqualTo("NEW");
        assertThat(Json.valueOf(new StringBuilder("sb")).textValue()).isEqualTo("sb");
        assertThat(Json.valueOf(7L).longValue()).isEqualTo(7);
        assertThatThrownBy(() -> Json.valueOf(new Object())).isInstanceOf(IllegalArgumentException.class);
        assertThat(Json.mapper()).isNotNull();
    }
}
