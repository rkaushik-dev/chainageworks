package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.app.Startup;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.http.ChainageworksApplication;
import io.github.rkaushikdev.chainageworks.store.FileStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The HTTP server's entry point: load the job the environment names, say what is being served, and
 * serve it until the server is closed.
 *
 * <p>The settings come from the environment alone, the same {@code CHAINAGEWORKS_} variables every
 * command reads. Every argument goes to the server untouched, so {@code --server.port=0} listens on
 * any free port.
 */
final class Serve {

    /** The word that starts the server rather than running a command: {@code cw serve}. */
    public static final String COMMAND = "serve";

    private Serve() {}

    /**
     * Serve the job the environment names until the server is closed.
     *
     * <p>What is being served is said before it is served, on the error stream: the job, the file it came
     * from and where the server listens. A server that starts silently on the wrong job file is the
     * easiest mistake to make here, because the file comes from an environment variable and the failure
     * looks like a valuation that is quietly out by a month. A job that cannot be valued at all is not
     * served.
     *
     * @param env the environment the config is read from
     * @param err where the startup report goes, a line at a time
     * @param started handed the running application once it is listening, to read the port it was given
     *     or to close it
     * @param args the server's own arguments, which override the config
     * @return one when the job cannot be valued and nothing was served, nought once the server has closed
     * @throws io.github.rkaushikdev.chainageworks.BadInputException when the environment is wrong or there
     *     is no job file to serve
     * @throws InterruptedException when the wait is interrupted, after the server has been closed
     */
    public static int run(
            Map<String, String> env,
            Consumer<String> err,
            Consumer<? super ConfigurableApplicationContext> started,
            List<String> args)
            throws InterruptedException {
        Config config = Config.fromEnv(env);
        Service service = new Service(FileStore.load(Path.of(config.jobFile())), config);
        Startup startup = Startup.report(service, config);
        startup.lines().forEach(err);
        err.accept(startup.summary());
        if (startup.refuses()) {
            return 1;
        }
        ConfigurableApplicationContext application = ChainageworksApplication.run(service, args.toArray(String[]::new));
        started.accept(application);
        ChainageworksApplication.awaitClose(application);
        return 0;
    }
}
