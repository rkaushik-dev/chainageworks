package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * The command tree, written the way its usage reads.
 *
 * <p>A command is declared as {@code strip <package> <valuation>} and an option as {@code -w, --weeks
 * <count>}, which is also exactly how the reference and the help print them. Declaring the tree in the
 * words it prints keeps the two from drifting: there is no second spelling of a flag anywhere.
 *
 * <p>Every value is kept as the text that was typed. The commands read numbers and dates themselves,
 * so a value the parser would have refused gets the same sentence as any other bad input rather than
 * a parser's complaint.
 */
final class Declare {

    /** What the help option and the help command both say about themselves. */
    static final String HELP_DESCRIPTION = "display help for command";

    private Declare() {}

    /**
     * A command whose usage is {@code usage}: its name, then one required argument a word, each written
     * {@code <name>}.
     */
    static CommandSpec command(String usage, String description, Action action, OptionSpec... options) {
        List<String> words = Text.split(usage, " ");
        CommandSpec command = CommandSpec.wrapWithoutInspection(action);
        command.name(words.get(0));
        command.usageMessage().description(description);
        for (int index = 1; index < words.size(); index++) {
            command.addPositional(PositionalParamSpec.builder()
                    .index(Integer.toString(index - 1))
                    .arity("1")
                    .required(true)
                    .paramLabel(words.get(index))
                    .type(String.class)
                    .build());
        }
        for (OptionSpec option : options) {
            command.addOption(option);
        }
        command.addOption(help());
        return command;
    }

    /** An option with no default, declared as {@code -w, --weeks <count>} or, for a flag, {@code --chase}. */
    static OptionSpec option(String flags, String description) {
        return option(flags, description, null);
    }

    /**
     * An option declared as {@code --late <periods>}, taking {@code defaultValue} when it is not given.
     * A flag is an option with no value written after its names.
     */
    static OptionSpec option(String flags, String description, String defaultValue) {
        List<String> names = new ArrayList<>();
        String label = null;
        for (String word : Text.split(flags.replace(", ", " "), " ")) {
            if (word.startsWith("<")) {
                label = word;
            } else {
                names.add(word);
            }
        }
        OptionSpec.Builder option =
                OptionSpec.builder(names.toArray(String[]::new)).description(description);
        if (label == null) {
            option.type(boolean.class).arity("0");
        } else {
            option.type(String.class).arity("1").paramLabel(label);
        }
        if (defaultValue != null) {
            option.defaultValue(defaultValue);
        }
        return option.build();
    }

    /** {@code -h, --help}, which every command takes. */
    static OptionSpec help() {
        return OptionSpec.builder("-h", "--help")
                .usageHelp(true)
                .description(HELP_DESCRIPTION)
                .build();
    }
}
