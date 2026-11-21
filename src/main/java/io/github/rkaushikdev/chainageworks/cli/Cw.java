package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.CwException;
import java.util.List;

/**
 * The {@code cw} process: the command line on the real disk, terminal and environment, or the HTTP
 * server when the first word is {@code serve}.
 *
 * <p>{@code cw serve} is not one of the commands. It takes none of the global flags, reads its settings
 * from the environment alone and hands the rest of the line to the server, so it is not in the command
 * reference either.
 */
public final class Cw {

    private Cw() {}

    /** Run the command line and exit with its code. */
    public static void main(String[] args) throws InterruptedException {
        System.exit(run(List.of(args), SystemDeps.ofProcess()));
    }

    /**
     * Run one command line against {@code deps}, or serve when the first word is {@code serve}. A server
     * that cannot start for want of a job file or a readable environment says why the way a command
     * does, and exits one.
     */
    static int run(List<String> argv, SystemDeps deps) throws InterruptedException {
        if (!argv.isEmpty() && argv.getFirst().equals(Serve.COMMAND)) {
            try {
                return Serve.run(deps.env(), deps::err, application -> {}, argv.subList(1, argv.size()));
            } catch (CwException refused) {
                deps.err(Program.complaint(refused));
                return 1;
            }
        }
        return Program.run(argv, deps);
    }
}
