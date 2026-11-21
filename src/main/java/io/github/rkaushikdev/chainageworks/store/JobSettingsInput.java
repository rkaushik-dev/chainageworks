package io.github.rkaushikdev.chainageworks.store;

import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.valuation.RetentionBond;
import io.github.rkaushikdev.chainageworks.valuation.RetentionTerms;
import java.time.LocalDate;
import java.util.List;

/**
 * What a job's settings are built from. Only the id, the name and the contract sum are needed; a
 * {@code null} anywhere else takes the contract default {@link JobSettings#build} names.
 *
 * @param id the job's key
 * @param name what the job is called
 * @param currency what the contract is priced in; the contract sum's currency when null
 * @param contractSum the tendered contract sum
 * @param retention the retention terms
 * @param retentionBond a bond in place of the cash retention, when the contract has one
 * @param materialsAllowance the share of materials on site that is certified
 * @param daywork the daywork schedule
 * @param completion completion date and liquidated damages
 * @param defectsPeriodEnds when the defects liability period ends
 * @param outputAllowances what each step of a rule of credit was expected to produce in an hour
 * @param adjustment the fluctuation clause
 * @param contraNotifyDays days to notify a subcontractor of a charge
 * @param riskMeetingDays days from an early warning to its meeting
 * @param haulRate what it costs to move a cubic metre one kilometre
 * @param haulLinks the haul distances between the sites on the job
 * @param tolerances how far a measurement may run past its allocation
 * @param workingDays the ISO days of the week the site works
 * @param cutOffDay the day of the month the works are measured to
 * @param issueDays working days from cut-off to the certificate
 * @param paymentDays calendar days from the certificate to payment
 * @param substantialCompletionOn when the works were substantially complete
 * @param defectsCertificateOn when the defects certificate was issued
 */
public record JobSettingsInput(
        String id,
        String name,
        Currency currency,
        Money contractSum,
        RetentionTerms retention,
        RetentionBond retentionBond,
        Percent materialsAllowance,
        DayworkSchedule daywork,
        CompletionTerms completion,
        LocalDate defectsPeriodEnds,
        List<OutputAllowance> outputAllowances,
        AdjustmentTerms adjustment,
        Integer contraNotifyDays,
        Integer riskMeetingDays,
        Money haulRate,
        List<HaulLink> haulLinks,
        ToleranceSet tolerances,
        List<Integer> workingDays,
        Integer cutOffDay,
        Integer issueDays,
        Integer paymentDays,
        LocalDate substantialCompletionOn,
        LocalDate defectsCertificateOn) {

    /** A job with every contract default. */
    public static JobSettingsInput of(String id, String name, Money contractSum) {
        return new JobSettingsInput(
                id,
                name,
                null,
                contractSum,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public JobSettingsInput withCurrency(Currency other) {
        Draft draft = new Draft(this);
        draft.currency = other;
        return draft.input();
    }

    public JobSettingsInput withContractSum(Money other) {
        Draft draft = new Draft(this);
        draft.contractSum = other;
        return draft.input();
    }

    public JobSettingsInput withMaterialsAllowance(Percent other) {
        Draft draft = new Draft(this);
        draft.materialsAllowance = other;
        return draft.input();
    }

    public JobSettingsInput withWorkingDays(List<Integer> other) {
        Draft draft = new Draft(this);
        draft.workingDays = other;
        return draft.input();
    }

    public JobSettingsInput withCutOffDay(int other) {
        Draft draft = new Draft(this);
        draft.cutOffDay = other;
        return draft.input();
    }

    public JobSettingsInput withIssueDays(int other) {
        Draft draft = new Draft(this);
        draft.issueDays = other;
        return draft.input();
    }

    public JobSettingsInput withPaymentDays(int other) {
        Draft draft = new Draft(this);
        draft.paymentDays = other;
        return draft.input();
    }

    public JobSettingsInput withSubstantialCompletionOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.substantialCompletionOn = other;
        return draft.input();
    }

    /** A copy of the input with one field changed, so each wither names only its own field. */
    private static final class Draft {
        private final String id;
        private final String name;
        private Currency currency;
        private Money contractSum;
        private final RetentionTerms retention;
        private final RetentionBond retentionBond;
        private Percent materialsAllowance;
        private final DayworkSchedule daywork;
        private final CompletionTerms completion;
        private final LocalDate defectsPeriodEnds;
        private final List<OutputAllowance> outputAllowances;
        private final AdjustmentTerms adjustment;
        private final Integer contraNotifyDays;
        private final Integer riskMeetingDays;
        private final Money haulRate;
        private final List<HaulLink> haulLinks;
        private final ToleranceSet tolerances;
        private List<Integer> workingDays;
        private Integer cutOffDay;
        private Integer issueDays;
        private Integer paymentDays;
        private LocalDate substantialCompletionOn;
        private final LocalDate defectsCertificateOn;

        private Draft(JobSettingsInput input) {
            id = input.id;
            name = input.name;
            currency = input.currency;
            contractSum = input.contractSum;
            retention = input.retention;
            retentionBond = input.retentionBond;
            materialsAllowance = input.materialsAllowance;
            daywork = input.daywork;
            completion = input.completion;
            defectsPeriodEnds = input.defectsPeriodEnds;
            outputAllowances = input.outputAllowances;
            adjustment = input.adjustment;
            contraNotifyDays = input.contraNotifyDays;
            riskMeetingDays = input.riskMeetingDays;
            haulRate = input.haulRate;
            haulLinks = input.haulLinks;
            tolerances = input.tolerances;
            workingDays = input.workingDays;
            cutOffDay = input.cutOffDay;
            issueDays = input.issueDays;
            paymentDays = input.paymentDays;
            substantialCompletionOn = input.substantialCompletionOn;
            defectsCertificateOn = input.defectsCertificateOn;
        }

        private JobSettingsInput input() {
            return new JobSettingsInput(
                    id,
                    name,
                    currency,
                    contractSum,
                    retention,
                    retentionBond,
                    materialsAllowance,
                    daywork,
                    completion,
                    defectsPeriodEnds,
                    outputAllowances,
                    adjustment,
                    contraNotifyDays,
                    riskMeetingDays,
                    haulRate,
                    haulLinks,
                    tolerances,
                    workingDays,
                    cutOffDay,
                    issueDays,
                    paymentDays,
                    substantialCompletionOn,
                    defectsCertificateOn);
        }
    }
}
