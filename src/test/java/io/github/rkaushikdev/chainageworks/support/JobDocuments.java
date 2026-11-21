package io.github.rkaushikdev.chainageworks.support;

import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.codec.JobCodec;
import io.github.rkaushikdev.chainageworks.util.Json;

/** Jobs taken through the job file's text, for the suites that check a register survives a save. */
public final class JobDocuments {

    private JobDocuments() {}

    /** The job written to its document text and read back, as a save followed by a load does. */
    public static Job throughText(Job job) {
        try {
            return JobCodec.fromJson(Json.parse(Json.stringify(JobCodec.toJson(job))));
        } catch (Json.JsonFailure broken) {
            throw new AssertionError("the job document did not read back", broken);
        }
    }
}
