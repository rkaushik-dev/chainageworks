package io.github.rkaushikdev.chainageworks.store;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.ErrorCode;
import io.github.rkaushikdev.chainageworks.store.codec.JobCodec;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The job on disk: one JSON file holding the whole job.
 *
 * <p>A save never leaves a half written file behind. The file is written under a temporary name in
 * the same directory, flushed to the disk, and only then moved over the target in one atomic step, so
 * a reader sees either the old job or the new one. Writing in place would leave a truncated file
 * behind after a power cut, and a truncated job file is a month of measurement nobody can get back.
 */
public final class FileStore {

    /** The name {@link #parse(String)} gives text that did not come from a file. */
    public static final String NO_PATH = "<memory>";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TEMP_NAME_BYTES = 6;

    private FileStore() {}

    /**
     * How a save writes the file.
     *
     * @param backup keep the previous file alongside the new one, as {@code <file>.bak}
     * @param pretty indent the JSON so a person can read it, rather than writing one line
     */
    public record SaveOptions(boolean backup, boolean pretty) {

        /** A backup of the previous file, and readable JSON. */
        public static final SaveOptions DEFAULT = new SaveOptions(true, true);

        public SaveOptions withBackup(boolean other) {
            return new SaveOptions(other, pretty);
        }

        public SaveOptions withPretty(boolean other) {
            return new SaveOptions(backup, other);
        }
    }

    /** Save with a backup of the previous file and readable JSON. */
    public static void save(Job job, Path path) throws IOException {
        save(job, path, SaveOptions.DEFAULT);
    }

    /** Write the job to {@code path}, making the directory if need be. */
    public static void save(Job job, Path path, SaveOptions options) throws IOException {
        String text = Json.stringify(JobCodec.toJson(job), options.pretty() ? 2 : 0);
        Path directory = path.toAbsolutePath().getParent();
        Files.createDirectories(directory);

        if (options.backup() && exists(path)) {
            Files.copy(path, backupOf(path), StandardCopyOption.REPLACE_EXISTING);
        }

        Path temporary = directory.resolve("." + HexFormat.of().formatHex(randomBytes()) + ".tmp");
        try {
            try (FileChannel channel =
                    FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap((text + "\n").getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failed.addSuppressed(cleanup);
            }
            throw failed;
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[TEMP_NAME_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static Path backupOf(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    /**
     * The job in the file at {@code path}.
     *
     * @throws BadInputException when there is no file there to read, or it is not a job file
     */
    public static Job load(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new BadInputException("no job file at that path", Details.of("path", path.toString()));
        }
        return parse(text, path.toString());
    }

    /** {@link #parse(String, String)} for text that did not come from a file. */
    public static Job parse(String text) {
        return parse(text, NO_PATH);
    }

    /**
     * The job in {@code text}, read as the file at {@code path}.
     *
     * <p>A file that is not JSON at all and a file that is JSON but not a job get different answers,
     * because the first is usually the wrong path and the second is usually a version mismatch. The
     * second names the file in its message.
     *
     * @throws BadInputException when the text is not JSON, or not a job document
     */
    public static Job parse(String text, String path) {
        JsonNode parsed;
        try {
            parsed = Json.parse(text);
        } catch (Json.JsonFailure broken) {
            throw new BadInputException("the job file is not valid json", Details.of("path", path));
        }
        try {
            return JobCodec.fromJson(parsed);
        } catch (CwException refused) {
            Map<String, Object> details = new LinkedHashMap<>(refused.details());
            details.put("path", path);
            throw new BadInputException(path + ": " + refused.getMessage(), details);
        }
    }

    /** Whether there is a file at {@code path}. */
    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    /**
     * Put the job back the way the last save found it.
     *
     * @throws BadInputException when there is no backup beside the file
     */
    public static void restoreBackup(Path path) throws IOException {
        Path backup = backupOf(path);
        if (!exists(backup)) {
            throw new BadInputException("there is no backup to restore", Details.of("path", backup.toString()));
        }
        Files.copy(backup, path, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Save, then read the file straight back and check the job still holds the same records.
     *
     * <p>Used before overwriting a month of work, because a codec that has quietly stopped round
     * tripping is silent until somebody reopens the file weeks later.
     *
     * @throws CwException with code {@code internal} when the file does not read back as written
     */
    public static void saveAndVerify(Job job, Path path) throws IOException {
        save(job, path);
        Job again = load(path);
        if (again.records().count() != job.records().count()) {
            throw new CwException(
                    ErrorCode.INTERNAL,
                    "the job did not read back the way it was written",
                    Details.of(
                            "path", path.toString(),
                            "wrote", job.records().count(),
                            "read", again.records().count()));
        }
    }
}
