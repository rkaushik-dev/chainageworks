package io.github.rkaushikdev.chainageworks.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * The command reference, read off the command tree rather than kept beside it.
 *
 * <p>A reference written by hand is a reference that stops matching the tool, and this one is read off
 * the same tree the parser uses. A command added without a description shows up here as an empty
 * cell, which is a visible gap rather than a silent omission.
 *
 * <p>The commands come in the order they were added to the tree, because the tree was built in the
 * order somebody uses it: load a job, look at it, value it, export it. Sorting the list alphabetically
 * would put the export commands first and the import commands in the middle, which teaches the reader
 * nothing.
 */
final class Reference {

    private Reference() {}

    /**
     * One option as the reference lists it.
     *
     * @param flags its names and its value, {@code -n, --periods <count>}
     * @param description what it does
     */
    public record OptionEntry(String flags, String description) {

        public OptionEntry {
            Objects.requireNonNull(flags, "flags");
            Objects.requireNonNull(description, "description");
        }

        /** The entry as the json flag prints it. */
        public ObjectNode toJson() {
            ObjectNode json = Json.object();
            json.put("flags", flags);
            json.put("description", description);
            return json;
        }
    }

    /**
     * One command as the reference lists it.
     *
     * @param name what is typed to run it
     * @param usage the whole line, {@code cw certificate [options] <valuation>}
     * @param description what it is for
     * @param options the options it takes of its own, leaving out the help option every command takes
     */
    public record CommandEntry(String name, String usage, String description, List<OptionEntry> options) {

        public CommandEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(description, "description");
            options = List.copyOf(options);
        }

        /** The entry as the json flag prints it. */
        public ObjectNode toJson() {
            ObjectNode json = Json.object();
            json.put("name", name);
            json.put("usage", usage);
            json.put("description", description);
            json.set(
                    "options",
                    Json.valueOf(options.stream().map(OptionEntry::toJson).toList()));
            return json;
        }
    }

    /** Every command on {@code program}, in the order they were added. */
    public static List<CommandEntry> commandEntries(CommandSpec program) {
        return commands(program)
                .map(command -> new CommandEntry(
                        command.name(),
                        Text.trim(program.name() + " " + command.name() + " " + usage(command)),
                        description(command),
                        entries(options(command))))
                .toList();
    }

    /** The global flags, which apply whichever command is run. */
    public static List<OptionEntry> globalOptions(CommandSpec program) {
        return entries(options(program));
    }

    /** The reference as a markdown document, ending in exactly one newline. */
    public static String markdown(CommandSpec program) {
        List<String> lines =
                new ArrayList<>(List.of("# " + program.name(), "", description(program), "", "## Global options", ""));
        globalOptions(program).forEach(option -> lines.add(bullet(option)));
        lines.addAll(List.of("", "## Commands", ""));
        for (CommandEntry entry : commandEntries(program)) {
            lines.addAll(List.of("### " + entry.name(), "", entry.description(), "", "```", entry.usage(), "```", ""));
            if (!entry.options().isEmpty()) {
                entry.options().forEach(option -> lines.add(bullet(option)));
                lines.add("");
            }
        }
        return Text.trimEnd(String.join("\n", lines)) + "\n";
    }

    private static String bullet(OptionEntry option) {
        return "- `" + option.flags() + "` " + option.description();
    }

    /** One line a command, the descriptions lined up past the longest name, which is what a terminal wants. */
    public static List<String> lines(CommandSpec program) {
        List<CommandEntry> entries = commandEntries(program);
        int width =
                entries.stream().mapToInt(entry -> entry.name().length()).max().orElse(0);
        return entries.stream()
                .map(entry -> Text.padEnd(entry.name(), width) + "  " + entry.description())
                .toList();
    }

    /**
     * The commands of {@code program} a reader is told about. The implicit help command is marked hidden
     * so it stays out of the reference, which lists the tool's own commands.
     */
    static Stream<CommandSpec> commands(CommandSpec program) {
        return program.subcommands().values().stream()
                .map(CommandLine::getCommandSpec)
                .filter(command -> !command.usageMessage().hidden());
    }

    /**
     * The options {@code command} takes of its own: not the help option, and not the hidden copies of the
     * global flags each command carries so they can be written after its name.
     */
    static List<OptionSpec> options(CommandSpec command) {
        return command.options().stream()
                .filter(option -> !option.hidden() && !option.usageHelp())
                .toList();
    }

    private static List<OptionEntry> entries(List<OptionSpec> options) {
        return options.stream()
                .map(option -> new OptionEntry(flags(option), description(option)))
                .toList();
    }

    /** An option's names and value as they are typed, {@code -w, --weeks <count>}. */
    static String flags(OptionSpec option) {
        String names = String.join(", ", option.names());
        return option.arity().max() > 0 ? names + " " + option.paramLabel() : names;
    }

    /** What follows a command's name in its usage line: {@code [options]}, then its arguments. */
    static String usage(CommandSpec command) {
        Stream<String> commandWord =
                Reference.commands(command).findAny().isPresent() ? Stream.of("[command]") : Stream.empty();
        return Stream.of(
                        Stream.of("[options]"),
                        commandWord,
                        command.positionalParameters().stream().map(PositionalParamSpec::paramLabel))
                .flatMap(words -> words)
                .collect(Collectors.joining(" "));
    }

    static String description(CommandSpec command) {
        return String.join("\n", command.usageMessage().description());
    }

    static String description(OptionSpec option) {
        return String.join("\n", option.description());
    }
}
