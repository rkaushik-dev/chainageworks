package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/**
 * The {@code cw} command tree, and one run of it.
 *
 * <p>The tree is read by picocli and every command is dispatched from here, so the exit codes are this
 * class's and nobody else's: nought when the command did its work or printed the help, one for a
 * command line that cannot be read or a refusal from the job, and two for a failure that is not one of
 * the job's own. A refusal is printed as {@code code: message} on the error stream, the same code the
 * HTTP API answers with.
 *
 * <p>The global flags can be written either side of the command name, and a later copy of a flag wins.
 * An argument starting {@code @} is an argument, not a file of arguments.
 */
public final class Program {

    /** What the tool is called on the command line. */
    public static final String NAME = "cw";

    /** What the tool is for, as the help and the reference open. */
    public static final String DESCRIPTION = "progress measurement and interim valuation for linear civil works";

    private static final String HELP = "help";

    private final CliDeps deps;
    private final CommandSpec tree;

    private Program(CliDeps deps) {
        this.deps = Objects.requireNonNull(deps, "deps");
        this.tree = buildTree();
    }

    /** The command tree, with every command bound to {@code deps}. */
    public static Program build(CliDeps deps) {
        return new Program(deps);
    }

    /** The command tree itself, which the reference is read off. */
    public CommandSpec tree() {
        return tree;
    }

    /** Run one command line against {@code deps} and give back the exit code. */
    public static int run(List<String> argv, CliDeps deps) {
        return build(deps).run(argv);
    }

    /** Run one command line and give back the exit code. */
    public int run(List<String> argv) {
        CommandLine line = new CommandLine(tree);
        line.setOverwrittenOptionsAllowed(true);
        line.setExpandAtFiles(false);
        try {
            return dispatch(line.parseArgs(argv.toArray(String[]::new)));
        } catch (ParameterException misuse) {
            deps.err(Help.misuse(misuse));
            return 1;
        } catch (IOException | RuntimeException failure) {
            CwException problem = CwException.from(failure);
            deps.err(problem.code().wire() + ": " + problem.getMessage());
            return problem.code() == ErrorCode.INTERNAL ? 2 : 1;
        }
    }

    private int dispatch(ParseResult parsed) throws IOException {
        if (parsed.isUsageHelpRequested()) {
            deps.out(Help.text(tree));
            return 0;
        }
        if (!parsed.hasSubcommand()) {
            // Nothing to run: say how the tool is used, on the error stream, without calling it a failure.
            deps.err(Help.text(tree));
            return 0;
        }
        ParseResult named = parsed.subcommand();
        CommandSpec command = named.commandSpec();
        if (named.isUsageHelpRequested()) {
            deps.out(Help.text(command));
            return 0;
        }
        if (command.userObject() instanceof Action action) {
            action.run(new Invocation(deps, parsed, named));
            return 0;
        }
        return help(named);
    }

    /** {@code cw help [command]}: the help of the command named, or the tool's own when it names none. */
    private int help(ParseResult named) {
        List<String> words = named.matchedPositionalValue(0, List.of());
        if (words.isEmpty()) {
            deps.out(Help.text(tree));
            return 0;
        }
        Optional<CommandSpec> command = Reference.commands(tree)
                .filter(candidate -> candidate.name().equals(words.getFirst()))
                .findFirst();
        command.ifPresentOrElse(found -> deps.out(Help.text(found)), () -> deps.err(Help.text(tree)));
        return 0;
    }

    /**
     * The tree, with the commands in the order the help and the reference list them: the job, the
     * reports on a valuation, the plans, the imports, then the exports and the checks run with them.
     */
    private static CommandSpec buildTree() {
        CommandSpec root = CommandSpec.create().name(NAME);
        root.usageMessage().description(DESCRIPTION);
        globals(false).forEach(root::addOption);
        root.addOption(Declare.help());

        List<CommandSpec> commands = new ArrayList<>(
                List.of(JobCommands.status(), JobCommands.config(), JobCommands.bill(), JobCommands.packages()));
        commands.addAll(ValuationCommands.all());
        commands.addAll(PlanCommands.all());
        commands.addAll(ImportCommands.all());
        commands.addAll(List.of(
                ExportCommands.export(),
                ExportCommands.csv(),
                JobCommands.commands(),
                JobCommands.revisions(),
                ExportCommands.monthEnd(),
                JobCommands.doctor(),
                JobCommands.reconcile()));
        for (CommandSpec command : commands) {
            globals(true).forEach(command::addOption);
            root.addSubcommand(command.name(), command);
        }
        root.addSubcommand(HELP, helpCommand());
        return root;
    }

    /**
     * The flags every command reads. The top of the tree lists them; each command carries hidden copies
     * of its own, so a flag written after the command name is read too.
     */
    private static List<OptionSpec> globals(boolean hidden) {
        return List.of(
                        Declare.option("-f, --file <path>", "the job file to read"),
                        Declare.option("--json", "print json rather than text"),
                        Declare.option("--markdown", "print markdown rather than plain text"))
                .stream()
                .map(option -> hidden ? option.toBuilder().hidden(true).build() : option)
                .toList();
    }

    /** {@code help [command]}, hidden so the reference, which lists the tool's own commands, leaves it out. */
    private static CommandSpec helpCommand() {
        CommandSpec help = CommandSpec.create().name(HELP);
        help.usageMessage().description(Declare.HELP_DESCRIPTION).hidden(true);
        help.addPositional(PositionalParamSpec.builder()
                .index("0..*")
                .arity("0..*")
                .paramLabel("[command]")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .build());
        return help;
    }
}
