package io.github.rkaushikdev.chainageworks.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigTest {

    /** The defaults as a tree, with {@code field} replaced. */
    private static ObjectNode defaultsWith(String field, Object value) {
        ObjectNode json = Config.DEFAULT.toJson();
        json.set(field, Json.valueOf(value));
        return json;
    }

    private static ObjectNode http(String host, double port) {
        ObjectNode json = Json.object();
        json.put("host", host);
        json.set("port", Json.number(port));
        return json;
    }

    @Nested
    class DefaultConfig {

        @Test
        void carriesAWorkingSetOfDefaults() {
            Config config = Config.defaults();
            assertThat(config.jobFile()).isEqualTo("chainageworks.json");
            assertThat(config.http()).isEqualTo(new Config.Http("127.0.0.1", 8730));
            assertThat(config.logLevel()).isEqualTo(LogLevel.INFO);
            assertThat(config.autoSave()).isTrue();
            assertThat(config.strictReconciliation()).isFalse();
        }

        @Test
        void handsBackACopySoACallerCannotEditTheDefaults() {
            // A config cannot be edited in place, so a caller's change is always a changed copy.
            Config config = Config.defaults();
            Config changed = config.merge(ConfigOverrides.none().withPort(9999));
            assertThat(changed.http().port()).isEqualTo(9999);
            assertThat(Config.DEFAULT.http().port()).isEqualTo(8730);
        }

        @Test
        void listensOnTheLoopbackAddressNotOnEverything() {
            assertThat(Config.DEFAULT.http().host()).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    class ParseConfig {

        @Test
        void acceptsTheDefaults() {
            assertThat(Config.parse(Config.DEFAULT.toJson())).isEqualTo(Config.DEFAULT);
        }

        @Test
        void namesTheFieldThatIsWrong() {
            assertThatThrownBy(() -> Config.parse(defaultsWith("jobFile", "")))
                    .hasMessageContaining("jobFile: a job file path is needed");
            assertThatThrownBy(() -> Config.parse(defaultsWith("http", http("x", 70_000))))
                    .hasMessageContaining("http.port");
            assertThatThrownBy(() -> Config.parse(defaultsWith("logLevel", "chatty")))
                    .hasMessageContaining("logLevel");
        }

        @Test
        void refusesAPortThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> Config.parse(defaultsWith("http", http("x", 80.5))))
                    .isInstanceOf(BadInputException.class);
        }

        @Test
        void refusesSomethingThatIsNotAConfigAtAll() {
            assertThatThrownBy(() -> Config.parse(NullNode.getInstance())).isInstanceOf(BadInputException.class);
            assertThatThrownBy(() -> Config.parse(Json.object())).isInstanceOf(BadInputException.class);
        }
    }

    @Nested
    class MergeConfig {

        @Test
        void takesTheOverrideAndKeepsTheRest() {
            Config merged = Config.DEFAULT.merge(ConfigOverrides.none().withJobFile("other.json"));
            assertThat(merged.jobFile()).isEqualTo("other.json");
            assertThat(merged.http().port()).isEqualTo(8730);
        }

        @Test
        void mergesTheHttpBlockAFieldAtATime() {
            Config merged = Config.DEFAULT.merge(ConfigOverrides.none().withPort(9000));
            assertThat(merged.http()).isEqualTo(new Config.Http("127.0.0.1", 9000));
        }

        @Test
        void validatesTheResult() {
            assertThatThrownBy(() -> Config.DEFAULT.merge(ConfigOverrides.none().withPort(-1)))
                    .hasMessageContaining("http.port");
        }
    }

    @Nested
    class ConfigFromEnv {

        @Test
        void keepsTheDefaultsForAnEmptyEnvironment() {
            assertThat(Config.fromEnv(Map.of())).isEqualTo(Config.DEFAULT);
        }

        @Test
        void readsEveryVariableItKnows() {
            Config config = Config.fromEnv(Map.of(
                    "CHAINAGEWORKS_JOB_FILE", "/srv/a1/job.json",
                    "CHAINAGEWORKS_HOST", "0.0.0.0",
                    "CHAINAGEWORKS_PORT", "9000",
                    "CHAINAGEWORKS_LOG_LEVEL", "debug",
                    "CHAINAGEWORKS_AUTO_SAVE", "off",
                    "CHAINAGEWORKS_STRICT", "yes"));
            assertThat(config.jobFile()).isEqualTo("/srv/a1/job.json");
            assertThat(config.http()).isEqualTo(new Config.Http("0.0.0.0", 9000));
            assertThat(config.logLevel()).isEqualTo(LogLevel.DEBUG);
            assertThat(config.autoSave()).isFalse();
            assertThat(config.strictReconciliation()).isTrue();
        }

        @Test
        void readsAFlagInAnyOfTheUsualSpellings() {
            for (String yes : List.of("1", "true", "YES", "on")) {
                assertThat(Config.fromEnv(Map.of("CHAINAGEWORKS_STRICT", yes)).strictReconciliation())
                        .as(yes)
                        .isTrue();
            }
            for (String no : List.of("0", "false", "NO", "off")) {
                assertThat(Config.fromEnv(Map.of("CHAINAGEWORKS_STRICT", no)).strictReconciliation())
                        .as(no)
                        .isFalse();
            }
        }

        @Test
        void treatsAnEmptyVariableAsAbsent() {
            assertThat(Config.fromEnv(Map.of("CHAINAGEWORKS_PORT", "", "CHAINAGEWORKS_STRICT", "")))
                    .isEqualTo(Config.DEFAULT);
        }

        @Test
        void namesTheVariableWhenItCannotReadIt() {
            assertThatThrownBy(() -> Config.fromEnv(Map.of("CHAINAGEWORKS_PORT", "eight thousand")))
                    .hasMessageContaining("CHAINAGEWORKS_PORT is not a whole number");
            assertThatThrownBy(() -> Config.fromEnv(Map.of("CHAINAGEWORKS_STRICT", "perhaps")))
                    .hasMessageContaining("CHAINAGEWORKS_STRICT is not a yes or a no");
            assertThatThrownBy(() -> Config.fromEnv(Map.of("CHAINAGEWORKS_LOG_LEVEL", "chatty")))
                    .hasMessageContaining("CHAINAGEWORKS_LOG_LEVEL is not a level");
        }

        @Test
        void layersOverABaseOtherThanTheDefaults() {
            Config base = Config.DEFAULT.merge(
                    ConfigOverrides.none().withJobFile("base.json").withLogLevel(LogLevel.WARN));
            Config config = Config.fromEnv(Map.of("CHAINAGEWORKS_PORT", "9100"), base);
            assertThat(config.jobFile()).isEqualTo("base.json");
            assertThat(config.logLevel()).isEqualTo(LogLevel.WARN);
            assertThat(config.http().port()).isEqualTo(9100);
        }
    }

    @Nested
    class ShouldLog {

        @Test
        void letsThroughEverythingAtOrUnderTheLevel() {
            Config debug = Config.DEFAULT.merge(ConfigOverrides.none().withLogLevel(LogLevel.DEBUG));
            assertThat(Arrays.stream(LogLevel.values())
                            .filter(level -> level != LogLevel.SILENT)
                            .allMatch(debug::shouldLog))
                    .isTrue();
        }

        @Test
        void stopsAtTheLevelThatIsSet() {
            Config warn = Config.DEFAULT.merge(ConfigOverrides.none().withLogLevel(LogLevel.WARN));
            assertThat(warn.shouldLog(LogLevel.ERROR)).isTrue();
            assertThat(warn.shouldLog(LogLevel.WARN)).isTrue();
            assertThat(warn.shouldLog(LogLevel.INFO)).isFalse();
            assertThat(warn.shouldLog(LogLevel.DEBUG)).isFalse();
        }

        @Test
        void saysNothingAtAllWhenItIsSilent() {
            Config silent = Config.DEFAULT.merge(ConfigOverrides.none().withLogLevel(LogLevel.SILENT));
            assertThat(silent.shouldLog(LogLevel.ERROR)).isFalse();
        }
    }

    @Nested
    class DescribeConfig {

        @Test
        void readsAsASettingsBlock() {
            assertThat(Config.DEFAULT.describe().split("\n", -1))
                    .containsExactly(
                            "job file            chainageworks.json",
                            "http                127.0.0.1:8730",
                            "log level           info",
                            "auto save           on",
                            "strict reconcile    off",
                            "periods             12",
                            "lookahead weeks     4",
                            "gangs               1",
                            "worst lines         10");
        }
    }
}
