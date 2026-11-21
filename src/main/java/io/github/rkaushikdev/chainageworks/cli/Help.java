package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * What the command line says about how it is used: the help text of a command, and the one line
 * printed for a command line it cannot read.
 *
 * <p>Both are read off the command tree, in the same words the reference uses. The help is laid out
 * for an 80 column terminal: a usage line, the description, then the options and the commands, each
 * description in a column of its own and wrapped at word breaks.
 */
final class Help {

    private static final int WIDTH = 80;
    private static final int INDENT = 2;
    private static final int GAP = 2;
    /** Below this many columns a description is left on one line rather than wrapped into a sliver. */
    private static final int NARROWEST_WRAP = 40;

    private Help() {}

    /** The help text for {@code command}, with no trailing newline. */
    static String text(CommandSpec command) {
        List<Map.Entry<String, String>> options = command.options().stream()
                .filter(option -> !option.hidden())
                .map(option -> Map.entry(Reference.flags(option), optionDescription(option)))
                .toList();
        // Every command, the help command included: hidden keeps it out of the reference, not the help.
        List<Map.Entry<String, String>> commands = command.subcommands().values().stream()
                .map(CommandLine::getCommandSpec)
                .map(sub -> Map.entry(commandTerm(sub), Reference.description(sub)))
                .toList();
        int termWidth = Stream.concat(options.stream(), commands.stream())
                .mapToInt(item -> item.getKey().length())
                .max()
                .orElse(0);

        List<String> lines = new ArrayList<>();
        lines.add("Usage: " + usageLine(command));
        lines.add("");
        String description = Reference.description(command);
        if (!description.isEmpty()) {
            lines.add(wrap(description, WIDTH));
            lines.add("");
        }
        section(lines, "Options:", options, termWidth);
        section(lines, "Commands:", commands, termWidth);
        return Text.trimEnd(String.join("\n", lines));
    }

    private static void section(
            List<String> lines, String title, List<Map.Entry<String, String>> items, int termWidth) {
        if (items.isEmpty()) {
            return;
        }
        lines.add(title);
        items.forEach(item -> lines.add(item(item.getKey(), termWidth, item.getValue())));
        lines.add("");
    }

    /** {@code cw certificate [options] <valuation>}: every command above this one, then its own usage. */
    private static String usageLine(CommandSpec command) {
        List<String> names = new ArrayList<>();
        for (CommandSpec at = command; at != null; at = at.parent()) {
            names.addFirst(at.name());
        }
        return String.join(" ", names) + " " + Reference.usage(command);
    }

    /** A command as the list of commands shows it: its name, {@code [options]} if it has any, its arguments. */
    private static String commandTerm(CommandSpec command) {
        List<String> words = new ArrayList<>(List.of(command.name()));
        if (!Reference.options(command).isEmpty()) {
            words.add("[options]");
        }
        command.positionalParameters().forEach(argument -> words.add(argument.paramLabel()));
        return String.join(" ", words);
    }

    private static String optionDescription(OptionSpec option) {
        String description = Reference.description(option);
        return option.defaultValue() == null
                ? description
                : description + " (default: \"" + option.defaultValue() + "\")";
    }

    /** One line of a list: the term padded to the column, then the description wrapped beside it. */
    private static String item(String term, int termWidth, String description) {
        String indent = " ".repeat(INDENT);
        if (description.isEmpty()) {
            return indent + term;
        }
        int room = WIDTH - termWidth - GAP - INDENT;
        String wrapped = wrap(description, room).replace("\n", "\n" + " ".repeat(termWidth + GAP));
        return indent + Text.padEnd(term, termWidth) + " ".repeat(GAP) + wrapped.replace("\n", "\n" + indent);
    }

    /**
     * {@code text} broken at spaces into lines of at most {@code width} columns, leaving a word longer
     * than the line on a line of its own. Left alone when the column is too narrow to wrap into.
     */
    static String wrap(String text, int width) {
        if (width < NARROWEST_WRAP) {
            return text;
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : Text.split(text, "\n")) {
            List<String> chunks = chunks(line);
            if (chunks.isEmpty()) {
                wrapped.add("");
                continue;
            }
            StringBuilder current = new StringBuilder(chunks.getFirst());
            for (String chunk : chunks.subList(1, chunks.size())) {
                if (current.length() + chunk.length() <= width) {
                    current.append(chunk);
                } else {
                    wrapped.add(current.toString());
                    current = new StringBuilder(Text.trimStart(chunk));
                }
            }
            wrapped.add(current.toString());
        }
        return String.join("\n", wrapped);
    }

    /** {@code line} cut into words, each carrying the spaces in front of it. */
    private static List<String> chunks(String line) {
        List<String> chunks = new ArrayList<>();
        int at = 0;
        while (at < line.length()) {
            int word = at;
            while (word < line.length() && Text.isSpace(line.charAt(word))) {
                word++;
            }
            if (word == line.length()) {
                break;
            }
            int end = word;
            while (end < line.length() && !Text.isSpace(line.charAt(end))) {
                end++;
            }
            chunks.add(line.substring(at, end));
            at = end;
        }
        return chunks;
    }

    /**
     * The line printed for a command line that cannot be read, naming what was wrong with it the way it
     * was typed, with a guess at what was meant when one is close.
     */
    static String misuse(ParameterException problem) {
        CommandSpec command = problem.getCommandLine().getCommandSpec();
        if (problem instanceof UnmatchedArgumentException unmatched) {
            return unmatched(command, unmatched.getUnmatched());
        }
        if (problem instanceof MissingParameterException missing
                && !missing.getMissing().isEmpty()) {
            ArgSpec first = missing.getMissing().getFirst();
            return first instanceof OptionSpec option
                    ? "error: option '" + Reference.flags(option) + "' argument missing"
                    : "error: missing required argument '" + bare(first.paramLabel()) + "'";
        }
        return "error: " + problem.getMessage();
    }

    /**
     * At the top of the tree a word that is not an option can only be a command that does not exist.
     * Under a command, an option it does not take is named before any arguments it has too many of.
     */
    private static String unmatched(CommandSpec command, List<String> words) {
        String first = words.getFirst();
        if (command.parent() == null && !looksLikeOption(first)) {
            List<String> names = command.subcommands().values().stream()
                    .map(sub -> sub.getCommandSpec().name())
                    .toList();
            return "error: unknown command '" + first + "'" + Suggestion.of(first, names);
        }
        Optional<String> option = words.stream().filter(Help::looksLikeOption).findFirst();
        if (option.isPresent()) {
            String flag = option.get();
            String guess = flag.startsWith("--") ? Suggestion.of(flag, longNames(command)) : "";
            return "error: unknown option '" + flag + "'" + guess;
        }
        int expected = command.positionalParameters().size();
        return "error: too many arguments for '" + command.name() + "'. Expected " + expected + " argument"
                + (expected == 1 ? "" : "s") + " but got " + (expected + words.size()) + ".";
    }

    /** A word that starts with a dash and has something after it, which is how an option is spotted. */
    private static boolean looksLikeOption(String word) {
        return word.length() > 1 && word.charAt(0) == '-';
    }

    /** The long names a command's help lists, its own and those of every command above it. */
    private static List<String> longNames(CommandSpec command) {
        List<String> names = new ArrayList<>();
        for (CommandSpec at = command; at != null; at = at.parent()) {
            at.options().stream()
                    .filter(option -> !option.hidden())
                    .map(OptionSpec::longestName)
                    .filter(name -> name.startsWith("--"))
                    .forEach(names::add);
        }
        return names;
    }

    private static String bare(String label) {
        return label.startsWith("<") && label.endsWith(">") ? label.substring(1, label.length() - 1) : label;
    }
}
