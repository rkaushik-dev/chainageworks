package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.config.ConfigOverrides;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;

/**
 * One run of one command: what was typed, the global flags, and the deps to do it with.
 *
 * <p>The global flags are read here, once the whole line has been parsed, rather than by each
 * command. They can be written either side of the command name, and a command that read them before
 * the parser had reached the end of the line would miss the ones written after it.
 */
final class Invocation {

    static final String FILE = "--file";
    static final String JSON = "--json";
    static final String MARKDOWN = "--markdown";

    private final CliDeps deps;
    private final CommandSpec tree;
    private final CommandSpec command;
    private final Optional<String> file;
    private final boolean json;
    private final boolean markdown;

    /**
     * The run {@code parsed} describes, where {@code parsed} is the whole line and {@code command} the
     * part of it the command was named in.
     */
    Invocation(CliDeps deps, ParseResult parsed, ParseResult command) {
        this.deps = Objects.requireNonNull(deps, "deps");
        this.tree = parsed.commandSpec();
        this.command = command.commandSpec();
        // Written after the command name wins, as the later of two copies of a flag always does.
        this.file = Optional.ofNullable(command.hasMatchedOption(FILE) ? value(this.command, FILE) : value(tree, FILE));
        this.json = command.hasMatchedOption(JSON) || parsed.hasMatchedOption(JSON);
        this.markdown = command.hasMatchedOption(MARKDOWN) || parsed.hasMatchedOption(MARKDOWN);
    }

    private static String value(CommandSpec spec, String name) {
        return spec.findOption(name).getValue();
    }

    CliDeps deps() {
        return deps;
    }

    /** The whole command tree, which the reference is read off. */
    CommandSpec tree() {
        return tree;
    }

    /** The argument at {@code index}, as it was typed. */
    String argument(int index) {
        return command.positionalParameters().get(index).getValue();
    }

    /** The value given to the option named {@code name}, or its default, or nothing. */
    Optional<String> option(String name) {
        return Optional.ofNullable(value(command, name));
    }

    /**
     * The valuation number typed as the argument at {@code index}.
     *
     * @throws BadInputException unless it reads as a whole number from one
     */
    int valuation(int index) {
        return Service.valuationNumber(Numbers.parse(argument(index)));
    }

    /**
     * The count given to the option named {@code name}, or {@code fallback} when it was not given.
     *
     * <p>The fallback comes from the config rather than from the command tree. A default written into
     * the tree would be a literal there, and the configured one would never be reached.
     *
     * @throws BadInputException unless the value reads as a whole number from one
     */
    int count(String name, int fallback) {
        Optional<String> raw = option(name).filter(text -> !text.isEmpty());
        if (raw.isEmpty()) {
            return fallback;
        }
        double value = Numbers.parse(raw.get());
        if (!Numbers.isInteger(value) || value < 1 || value > Integer.MAX_VALUE) {
            throw new BadInputException("that has to be a whole number from one", Details.of("value", raw.get()));
        }
        return (int) value;
    }

    /** Whether the flag named {@code name} was given. */
    boolean flag(String name) {
        OptionSpec option = command.findOption(name);
        return Boolean.TRUE.equals(option.getValue());
    }

    /** Whether the markdown flag was given. */
    boolean markdown() {
        return markdown;
    }

    /**
     * The settings this run would use: the environment, with the job file from the file flag when it
     * was given.
     *
     * @throws io.github.rkaushikdev.chainageworks.BadInputException naming the variable or field that is
     *     wrong
     */
    Config config() {
        Config base = Config.fromEnv(deps.env());
        return file.map(path -> base.merge(ConfigOverrides.none().withJobFile(path)))
                .orElse(base);
    }

    /** The service over the job this run's settings name, loaded only when a command asks for it. */
    Service load() {
        Config config = config();
        return new Service(deps.loadJob(config.jobFile()), config);
    }

    /**
     * Print {@code text}, or {@code value} as indented JSON when the json flag was given. The value is
     * anything {@link Json#valueOf} takes: a tree, a number, a string, a list or {@code null}.
     */
    void print(String text, Object value) {
        deps.out(json ? Json.stringify(Json.valueOf(value), 2) : text);
    }
}
