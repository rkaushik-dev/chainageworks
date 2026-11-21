package io.github.rkaushikdev.chainageworks.progress;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a step was expected to produce in an hour.
 *
 * <p>Held per step of the rule of credit rather than per gang, because that is how a tender is
 * priced: the estimator allows so many hours to trim so many metres of formation, and never allows
 * hours to a particular gang. Keying it on the gang would make the comparison impossible the first
 * time the foreman moved somebody between two jobs.
 *
 * @param stepId the step
 * @param metresPerHour metres of the package expected for each labour hour booked
 */
public record OutputAllowance(String stepId, double metresPerHour) {

    /** Check a set of allowances, refusing two for one step and one of nothing an hour. */
    public static List<OutputAllowance> build(List<OutputAllowance> allowances) {
        Set<String> seen = new HashSet<>();
        for (OutputAllowance allowance : allowances) {
            if (seen.contains(allowance.stepId())) {
                throw new BadInputException("a step has two output allowances", Details.of("step", allowance.stepId()));
            }
            if (!Double.isFinite(allowance.metresPerHour()) || allowance.metresPerHour() <= 0) {
                throw new BadInputException(
                        "an output allowance has to be more than nothing an hour",
                        Details.of("step", allowance.stepId(), "metresPerHour", allowance.metresPerHour()));
            }
            seen.add(allowance.stepId());
        }
        return List.copyOf(allowances);
    }
}
