package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import java.util.List;
import java.util.OptionalDouble;

/**
 * What is left to build on one step, and how long it should take.
 *
 * <p>The remaining extent is what has not been built, not what has not been credited. A length
 * held back by an unsigned hold point has already been built, and putting it on a lookahead sends a
 * gang to do it twice.
 *
 * @param packageId the package
 * @param stepId the step
 * @param remaining the stretches still to build
 * @param remainingMetres their length in metres
 * @param days working days at the allowance, absent when no allowance is set
 * @param waitingOn steps of this package that have to finish first and have not
 * @param heldPoints hold points holding the step up rather than the work itself
 */
public record StepLookahead(
        String packageId,
        String stepId,
        List<ChainageRange> remaining,
        double remainingMetres,
        OptionalDouble days,
        List<String> waitingOn,
        List<String> heldPoints) {

    public StepLookahead {
        remaining = List.copyOf(remaining);
        waitingOn = List.copyOf(waitingOn);
        heldPoints = List.copyOf(heldPoints);
    }
}
