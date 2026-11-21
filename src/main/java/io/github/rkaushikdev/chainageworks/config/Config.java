package io.github.rkaushikdev.chainageworks.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Everything the process needs that is not part of the contract.
 *
 * <p>Kept apart from the job settings on purpose. A port number and a file path belong to the machine
 * the tool is running on; a retention percentage belongs to the contract, and putting the two in one
 * file makes it far too easy to copy a colleague's setup and quietly inherit their retention terms.
 *
 * <p>The record holds whatever it is given; {@link #check}, {@link #parse}, {@link #merge} and {@link
 * #fromEnv} are where a config is validated, and their refusals name the field, because the usual way
 * this fails is a port number arriving as text out of an environment variable and the operator needing
 * to know which variable to fix.
 *
 * @param jobFile the job file path
 * @param http where the service listens
 * @param logLevel how much the process says
 * @param autoSave write the job file after every command that changes it
 * @param strictReconciliation refuse to serve a valuation while the register does not reconcile
 * @param reports what a report does when nobody says otherwise
 */
public record Config(
        String jobFile, Http http, LogLevel logLevel, boolean autoSave, boolean strictReconciliation, Reports reports) {

    /** The prefix every environment variable the config reads carries. */
    public static final String ENV_PREFIX = "CHAINAGEWORKS_";

    /** The defaults: a job file in the working directory, served on the loopback address. */
    public static final Config DEFAULT = new Config(
            "chainageworks.json", new Http("127.0.0.1", 8730), LogLevel.INFO, true, false, new Reports(12, 4, 1, 10));

    private static final int LABEL_WIDTH = 20;
    private static final int LAST_PORT = 65_535;
    private static final int MOST_PERIODS = 600;
    private static final int MOST_LOOKAHEAD_WEEKS = 52;
    private static final int MOST_GANGS = 50;
    private static final int MOST_WORST_LINES = 500;
    private static final List<String> YES = List.of("1", "true", "yes", "on");
    private static final List<String> NO = List.of("0", "false", "no", "off");
    private static final String LEVELS = Arrays.stream(LogLevel.values())
            .map(level -> "'" + level.wire() + "'")
            .collect(Collectors.joining(" | "));

    public Config {
        Objects.requireNonNull(jobFile, "jobFile");
        Objects.requireNonNull(http, "http");
        Objects.requireNonNull(logLevel, "logLevel");
        Objects.requireNonNull(reports, "reports");
    }

    /**
     * Where the service listens.
     *
     * @param host the address
     * @param port the port, nought to 65535
     */
    public record Http(String host, int port) {

        public Http {
            Objects.requireNonNull(host, "host");
        }
    }

    /**
     * What a report does when nobody says otherwise.
     *
     * <p>Here rather than as literals in the command tree because these are the numbers a commercial
     * team argues about once and then wants everywhere. A job run on a four week cycle wants a four week
     * lookahead from every command, not from the ones somebody remembered to pass a flag to.
     *
     * @param periods periods a curve or a cash forecast runs to, one to 600
     * @param lookaheadWeeks weeks a lookahead covers, one to 52
     * @param gangs gangs a lookahead assumes are available for each step, one to 50
     * @param worstLines moving lines a final account prints before it stops, one to 500
     */
    public record Reports(int periods, int lookaheadWeeks, int gangs, int worstLines) {}

    /** The defaults. The record cannot be changed in place, so the shared defaults are safe to hand out. */
    public static Config defaults() {
        return DEFAULT;
    }

    /**
     * Check a config, handing the same config back.
     *
     * @throws BadInputException naming the first field that is out of bounds, as {@code http.port: ...}
     */
    public static Config check(Config config) {
        if (config.jobFile().isEmpty()) {
            throw issue("jobFile", "a job file path is needed");
        }
        if (config.http().host().isEmpty()) {
            throw issue("http.host", "String must contain at least 1 character(s)");
        }
        within("http.port", config.http().port(), 0, LAST_PORT);
        within("reports.periods", config.reports().periods(), 1, MOST_PERIODS);
        within("reports.lookaheadWeeks", config.reports().lookaheadWeeks(), 1, MOST_LOOKAHEAD_WEEKS);
        within("reports.gangs", config.reports().gangs(), 1, MOST_GANGS);
        within("reports.worstLines", config.reports().worstLines(), 1, MOST_WORST_LINES);
        return config;
    }

    private static void within(String field, int value, int least, int most) {
        if (value < least) {
            throw issue(field, "Number must be greater than or equal to " + least);
        }
        if (value > most) {
            throw issue(field, "Number must be less than or equal to " + most);
        }
    }

    private static BadInputException issue(String field, String message) {
        return new BadInputException(field + ": " + message, Details.of("field", field));
    }

    /**
     * Read a config from a JSON tree and check it. Fields the config does not have are ignored.
     *
     * @throws BadInputException naming the first field that is missing, of the wrong kind or out of
     *     bounds
     */
    public static Config parse(JsonNode value) {
        JsonNode root = object(value, "config");
        String jobFile = text(root.path("jobFile"), "jobFile");
        JsonNode http = object(root.path("http"), "http");
        Http listen = new Http(text(http.path("host"), "http.host"), integer(http.path("port"), "http.port"));
        LogLevel logLevel = level(root.path("logLevel"));
        boolean autoSave = flag(root.path("autoSave"), "autoSave");
        boolean strictReconciliation = flag(root.path("strictReconciliation"), "strictReconciliation");
        JsonNode reports = object(root.path("reports"), "reports");
        return check(new Config(
                jobFile,
                listen,
                logLevel,
                autoSave,
                strictReconciliation,
                new Reports(
                        integer(reports.path("periods"), "reports.periods"),
                        integer(reports.path("lookaheadWeeks"), "reports.lookaheadWeeks"),
                        integer(reports.path("gangs"), "reports.gangs"),
                        integer(reports.path("worstLines"), "reports.worstLines"))));
    }

    private static String kindOf(JsonNode value) {
        return switch (value.getNodeType()) {
            case ARRAY -> "array";
            case BOOLEAN -> "boolean";
            case NULL -> "null";
            case NUMBER -> Double.isNaN(value.doubleValue()) ? "nan" : "number";
            case STRING -> "string";
            default -> "object";
        };
    }

    private static JsonNode present(JsonNode value, String field) {
        if (value == null || value.isMissingNode()) {
            throw issue(field, "Required");
        }
        return value;
    }

    private static JsonNode object(JsonNode value, String field) {
        JsonNode present = present(value, field);
        if (!present.isObject()) {
            throw issue(field, "Expected object, received " + kindOf(present));
        }
        return present;
    }

    private static String text(JsonNode value, String field) {
        JsonNode present = present(value, field);
        if (!present.isTextual()) {
            throw issue(field, "Expected string, received " + kindOf(present));
        }
        return present.textValue();
    }

    private static int integer(JsonNode value, String field) {
        JsonNode present = present(value, field);
        if (!present.isNumber() || Double.isNaN(present.doubleValue())) {
            throw issue(field, "Expected number, received " + kindOf(present));
        }
        double number = present.doubleValue();
        if (!Numbers.isInteger(number)) {
            throw issue(field, "Expected integer, received float");
        }
        return saturated(number);
    }

    private static boolean flag(JsonNode value, String field) {
        JsonNode present = present(value, field);
        if (!present.isBoolean()) {
            throw issue(field, "Expected boolean, received " + kindOf(present));
        }
        return present.booleanValue();
    }

    private static LogLevel level(JsonNode value) {
        JsonNode present = present(value, "logLevel");
        if (!present.isTextual()) {
            throw issue("logLevel", "Expected " + LEVELS + ", received " + kindOf(present));
        }
        String text = present.textValue();
        return LogLevel.fromWire(text)
                .orElseThrow(() ->
                        issue("logLevel", "Invalid enum value. Expected " + LEVELS + ", received '" + text + "'"));
    }

    /** A whole number as an int, held at the int range so a huge value stays out of bounds. */
    private static int saturated(double whole) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, whole));
    }

    /** The config as a JSON tree, in the order {@link #parse} reads it. */
    public ObjectNode toJson() {
        ObjectNode json = Json.object();
        json.put("jobFile", jobFile);
        ObjectNode listen = json.putObject("http");
        listen.put("host", http.host());
        listen.put("port", http.port());
        json.put("logLevel", logLevel.wire());
        json.put("autoSave", autoSave);
        json.put("strictReconciliation", strictReconciliation);
        ObjectNode defaults = json.putObject("reports");
        defaults.put("periods", reports.periods());
        defaults.put("lookaheadWeeks", reports.lookaheadWeeks());
        defaults.put("gangs", reports.gangs());
        defaults.put("worstLines", reports.worstLines());
        return json;
    }

    /**
     * This config with the overrides applied, checked.
     *
     * @throws BadInputException naming the first field the result has out of bounds
     */
    public Config merge(ConfigOverrides overrides) {
        return check(new Config(
                orElse(overrides.jobFile(), jobFile),
                new Http(orElse(overrides.host(), http.host()), orElse(overrides.port(), http.port())),
                orElse(overrides.logLevel(), logLevel),
                orElse(overrides.autoSave(), autoSave),
                orElse(overrides.strictReconciliation(), strictReconciliation),
                new Reports(
                        orElse(overrides.periods(), reports.periods()),
                        orElse(overrides.lookaheadWeeks(), reports.lookaheadWeeks()),
                        orElse(overrides.gangs(), reports.gangs()),
                        orElse(overrides.worstLines(), reports.worstLines()))));
    }

    private static <T> T orElse(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /** {@link #fromEnv(Map, Config)} over the defaults. */
    public static Config fromEnv(Map<String, String> env) {
        return fromEnv(env, DEFAULT);
    }

    /**
     * Read the environment over {@code base}. Anything absent keeps what the base has, and so does a
     * variable set to nothing, except the log level, which has to be one the tool knows.
     *
     * <p>The variables are {@code CHAINAGEWORKS_} followed by {@code JOB_FILE}, {@code HOST}, {@code
     * PORT}, {@code LOG_LEVEL}, {@code AUTO_SAVE}, {@code STRICT}, {@code PERIODS}, {@code
     * LOOKAHEAD_WEEKS}, {@code GANGS} and {@code WORST_LINES}. A flag reads {@code 1}, {@code true},
     * {@code yes} or {@code on} as yes and {@code 0}, {@code false}, {@code no} or {@code off} as no, in
     * any case.
     *
     * @throws BadInputException naming the variable it cannot read, or the field the result has out of
     *     bounds
     */
    public static Config fromEnv(Map<String, String> env, Config base) {
        String level = env.get(ENV_PREFIX + "LOG_LEVEL");
        if (level != null && LogLevel.fromWire(level).isEmpty()) {
            throw new BadInputException(
                    ENV_PREFIX + "LOG_LEVEL is not a level this tool knows", Details.of("value", level));
        }
        return base.merge(new ConfigOverrides(
                given(env.get(ENV_PREFIX + "JOB_FILE")),
                given(env.get(ENV_PREFIX + "HOST")),
                readPort(env, "PORT"),
                level == null ? null : LogLevel.fromWire(level).orElseThrow(),
                readFlag(env, "AUTO_SAVE"),
                readFlag(env, "STRICT"),
                readCount(env, "PERIODS"),
                readCount(env, "LOOKAHEAD_WEEKS"),
                readCount(env, "GANGS"),
                readCount(env, "WORST_LINES")));
    }

    /** The variable's value, or {@code null} when it is absent or set to nothing. */
    private static String given(String raw) {
        return raw == null || raw.isEmpty() ? null : raw;
    }

    private static Boolean readFlag(Map<String, String> env, String variable) {
        String raw = given(env.get(ENV_PREFIX + variable));
        if (raw == null) {
            return null;
        }
        String cleaned = Text.lower(Text.trim(raw));
        if (YES.contains(cleaned)) {
            return true;
        }
        if (NO.contains(cleaned)) {
            return false;
        }
        throw new BadInputException(ENV_PREFIX + variable + " is not a yes or a no", Details.of("value", raw));
    }

    private static Integer readCount(Map<String, String> env, String variable) {
        String raw = given(env.get(ENV_PREFIX + variable));
        if (raw == null) {
            return null;
        }
        double value = Numbers.parse(raw);
        if (!Numbers.isInteger(value) || value < 1) {
            throw new BadInputException(
                    ENV_PREFIX + variable + " is not a whole number from one", Details.of("value", raw));
        }
        return saturated(value);
    }

    private static Integer readPort(Map<String, String> env, String variable) {
        String raw = given(env.get(ENV_PREFIX + variable));
        if (raw == null) {
            return null;
        }
        double value = Numbers.parse(raw);
        if (!Numbers.isInteger(value)) {
            throw new BadInputException(ENV_PREFIX + variable + " is not a whole number", Details.of("value", raw));
        }
        return saturated(value);
    }

    /** The config as a settings block, one labelled line a setting. */
    public String describe() {
        return String.join(
                "\n",
                line("job file", jobFile),
                line("http", http.host() + ":" + http.port()),
                line("log level", logLevel.wire()),
                line("auto save", autoSave ? "on" : "off"),
                line("strict reconcile", strictReconciliation ? "on" : "off"),
                line("periods", Integer.toString(reports.periods())),
                line("lookahead weeks", Integer.toString(reports.lookaheadWeeks())),
                line("gangs", Integer.toString(reports.gangs())),
                line("worst lines", Integer.toString(reports.worstLines())));
    }

    private static String line(String label, String value) {
        return Text.padEnd(label, LABEL_WIDTH) + value;
    }

    /**
     * Whether a message at {@code level} is let through: everything at or under the level that is set,
     * and nothing at all when it is silent.
     *
     * @throws IllegalArgumentException for {@link LogLevel#SILENT}, which no message is written at
     */
    public boolean shouldLog(LogLevel level) {
        if (level == LogLevel.SILENT) {
            throw new IllegalArgumentException("a message is written at a level, never at silent");
        }
        return logLevel.compareTo(level) >= 0;
    }
}
