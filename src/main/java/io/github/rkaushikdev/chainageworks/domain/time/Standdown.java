package io.github.rkaushikdev.chainageworks.domain.time;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A day the works were shut down, taken from the site diary.
 *
 * <p>A standdown is not a holiday. The day was a working day, the crew were there, and nothing was
 * built. That difference matters twice over: it is what an extension of time claim is built on,
 * and it is why a period with the same number of working days can carry very different progress.
 *
 * @param date the day lost
 * @param reason why it was lost
 * @param note what the diary says, if anything
 */
public record Standdown(LocalDate date, StanddownReason reason, Optional<String> note) {

    public Standdown {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(reason, "reason");
        note = note == null ? Optional.empty() : note;
    }

    /** A standdown with no note. */
    public Standdown(LocalDate date, StanddownReason reason) {
        this(date, reason, Optional.empty());
    }

    /** A standdown with a note. */
    public Standdown(LocalDate date, StanddownReason reason, String note) {
        this(date, reason, Optional.ofNullable(note));
    }
}
