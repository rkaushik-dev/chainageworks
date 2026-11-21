package io.github.rkaushikdev.chainageworks.app;

import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.store.Job;
import java.util.ArrayList;
import java.util.List;

/**
 * What the server says about itself before it takes a request.
 *
 * <p>Printed to the error stream rather than logged, because the only reader is whoever just typed the
 * command and they are looking at a terminal. A structured log line here would be read by nobody and
 * parsed by nothing.
 *
 * @param lines the report, one line each, in the order they print
 * @param refuses true when the job has a problem the server should not serve over
 */
public record Startup(List<String> lines, boolean refuses) {

    public Startup {
        lines = List.copyOf(lines);
    }

    /**
     * The report on {@code service}'s job as the server configured by {@code config} would serve it.
     *
     * <p>A job with a blocker cannot value anything, so the server refuses rather than answering five
     * hundreds all afternoon. A job with a dangling reference still serves: the numbers it gives are
     * wrong in one place and right everywhere else, and refusing the whole thing helps nobody.
     */
    public static Startup report(Service service, Config config) {
        Job job = service.job();
        Diagnosis diagnosis = Doctor.diagnose(job);
        List<String> blockers = diagnosis.blockers();

        List<String> lines = new ArrayList<>(List.of(
                "chainageworks serving " + config.jobFile(),
                "job          " + job.settings().id() + " " + job.settings().name(),
                "contract sum " + job.settings().contractSum().format(),
                "packages     " + job.packages().count(),
                "records      " + job.records().count(),
                "listening    " + config.http().host() + ":" + config.http().port()));

        if (!blockers.isEmpty()) {
            lines.add("blockers     " + String.join("; ", blockers));
        }
        if (diagnosis.errors() > 0) {
            lines.add("errors       " + diagnosis.errors() + ", run cw doctor");
        }
        if (diagnosis.warnings() > 0) {
            lines.add("warnings     " + diagnosis.warnings());
        }
        return new Startup(lines, !blockers.isEmpty() || !job.isReady());
    }

    /** The one line a supervisor watching the error stream needs to see. */
    public String summary() {
        return refuses ? "refusing to serve, the job cannot be valued" : "ready";
    }

    /** True when the doctor finds nothing wrong with the job, which is a different question from whether it serves. */
    public static boolean isSound(Service service) {
        return Doctor.diagnose(service.job()).isHealthy();
    }
}
