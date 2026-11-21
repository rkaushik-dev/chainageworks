package io.github.rkaushikdev.chainageworks.config;

/**
 * What to change in a {@link Config}. A {@code null} keeps what the base has.
 *
 * @param jobFile the job file path
 * @param host the address the service listens on
 * @param port the port the service listens on
 * @param logLevel how much the process says
 * @param autoSave whether a command that changes the job writes the file
 * @param strictReconciliation whether a valuation is refused while the register does not reconcile
 * @param periods periods a curve or a cash forecast runs to
 * @param lookaheadWeeks weeks a lookahead covers
 * @param gangs gangs a lookahead assumes for each step
 * @param worstLines moving lines a final account prints
 */
public record ConfigOverrides(
        String jobFile,
        String host,
        Integer port,
        LogLevel logLevel,
        Boolean autoSave,
        Boolean strictReconciliation,
        Integer periods,
        Integer lookaheadWeeks,
        Integer gangs,
        Integer worstLines) {

    /** Overrides that change nothing. */
    public static ConfigOverrides none() {
        return new ConfigOverrides(null, null, null, null, null, null, null, null, null, null);
    }

    public ConfigOverrides withJobFile(String other) {
        return new ConfigOverrides(
                other,
                host,
                port,
                logLevel,
                autoSave,
                strictReconciliation,
                periods,
                lookaheadWeeks,
                gangs,
                worstLines);
    }

    public ConfigOverrides withHost(String other) {
        return new ConfigOverrides(
                jobFile,
                other,
                port,
                logLevel,
                autoSave,
                strictReconciliation,
                periods,
                lookaheadWeeks,
                gangs,
                worstLines);
    }

    public ConfigOverrides withPort(int other) {
        return new ConfigOverrides(
                jobFile,
                host,
                other,
                logLevel,
                autoSave,
                strictReconciliation,
                periods,
                lookaheadWeeks,
                gangs,
                worstLines);
    }

    public ConfigOverrides withLogLevel(LogLevel other) {
        return new ConfigOverrides(
                jobFile, host, port, other, autoSave, strictReconciliation, periods, lookaheadWeeks, gangs, worstLines);
    }

    public ConfigOverrides withStrictReconciliation(boolean other) {
        return new ConfigOverrides(
                jobFile, host, port, logLevel, autoSave, other, periods, lookaheadWeeks, gangs, worstLines);
    }

    public ConfigOverrides withPeriods(int other) {
        return new ConfigOverrides(
                jobFile,
                host,
                port,
                logLevel,
                autoSave,
                strictReconciliation,
                other,
                lookaheadWeeks,
                gangs,
                worstLines);
    }

    public ConfigOverrides withLookaheadWeeks(int other) {
        return new ConfigOverrides(
                jobFile, host, port, logLevel, autoSave, strictReconciliation, periods, other, gangs, worstLines);
    }
}
