package io.github.rkaushikdev.chainageworks.app;

import java.util.List;

/**
 * Everything the doctor found on a job.
 *
 * @param findings every dangling reference, in the order the registers are walked
 * @param errors how many of the findings are errors
 * @param warnings how many of the findings are warnings
 * @param blockers what the job is missing before it can value anything
 */
public record Diagnosis(List<Finding> findings, int errors, int warnings, List<String> blockers) {

    public Diagnosis {
        findings = List.copyOf(findings);
        blockers = List.copyOf(blockers);
    }

    /** The findings and blockers, with the errors and warnings counted. */
    public static Diagnosis of(List<Finding> findings, List<String> blockers) {
        return new Diagnosis(
                findings, count(findings, FindingLevel.ERROR), count(findings, FindingLevel.WARNING), blockers);
    }

    private static int count(List<Finding> findings, FindingLevel level) {
        return (int) findings.stream().filter(f -> f.level() == level).count();
    }

    /** True when the job has no errors and nothing blocking it. Warnings do not count against it. */
    public boolean isHealthy() {
        return errors == 0 && blockers.isEmpty();
    }

    /** The findings from one register, which is how the list gets handed out. */
    public List<Finding> findingsIn(String register) {
        return findings.stream().filter(f -> f.register().equals(register)).toList();
    }
}
