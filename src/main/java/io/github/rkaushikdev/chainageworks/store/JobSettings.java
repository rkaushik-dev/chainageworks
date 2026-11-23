package io.github.rkaushikdev.chainageworks.store;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.valuation.InterimTerms;
import io.github.rkaushikdev.chainageworks.valuation.RetentionBond;
import io.github.rkaushikdev.chainageworks.valuation.RetentionTerms;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything about the contract that is not a record of what happened.
 *
 * <p>The withers change one field and check nothing, the way a hand edit of the settings does; {@link
 * #build} is where a new set is checked.
 *
 * @param id the job's key
 * @param name what the job is called
 * @param currency what the contract is priced in
 * @param contractSum the tendered contract sum
 * @param retention the retention terms
 * @param retentionBond a bond standing in place of the cash retention, when the contract has one
 * @param materialsAllowance the share of materials on site that is certified, when the contract fixes it
 * @param daywork the daywork schedule, when the contract has one
 * @param completion completion date and liquidated damages, when the contract fixes them
 * @param defectsPeriodEnds when the defects liability period ends, if it has been fixed
 * @param outputAllowances what each step of a rule of credit was expected to produce in an hour
 * @param adjustment the fluctuation clause, when the contract carries one
 * @param contraNotifyDays days to notify a subcontractor of a charge the order does not cover
 * @param riskMeetingDays days from an early warning to the meeting the contract wants
 * @param haulRate what it costs to move a cubic metre one kilometre
 * @param haulLinks the haul distances between the sites on the job
 * @param tolerances how far a measurement may run past its allocation
 * @param workingDays the ISO days of the week the site works, Monday being one
 * @param cutOffDay the day of the month the works are measured to
 * @param issueDays working days from cut-off to the certificate being issued
 * @param paymentDays calendar days from issue to payment falling due
 * @param substantialCompletionOn when the works were substantially complete
 * @param defectsCertificateOn when the defects certificate was issued
 * @param interimTerms the minimum certificate and the other terms on issuing interim certificates
 */
public record JobSettings(
        String id,
        String name,
        Currency currency,
        Money contractSum,
        RetentionTerms retention,
        Optional<RetentionBond> retentionBond,
        Optional<Percent> materialsAllowance,
        Optional<DayworkSchedule> daywork,
        Optional<CompletionTerms> completion,
        Optional<LocalDate> defectsPeriodEnds,
        List<OutputAllowance> outputAllowances,
        Optional<AdjustmentTerms> adjustment,
        int contraNotifyDays,
        int riskMeetingDays,
        Money haulRate,
        List<HaulLink> haulLinks,
        ToleranceSet tolerances,
        List<Integer> workingDays,
        int cutOffDay,
        int issueDays,
        int paymentDays,
        Optional<LocalDate> substantialCompletionOn,
        Optional<LocalDate> defectsCertificateOn,
        InterimTerms interimTerms) {

    private static final int DEFAULT_CUT_OFF_DAY = 25;
    private static final int DEFAULT_ISSUE_DAYS = 7;
    private static final int DEFAULT_PAYMENT_DAYS = 14;
    private static final int LAST_DAY_OF_ANY_MONTH = 31;

    public JobSettings {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(contractSum, "contractSum");
        retentionBond = retentionBond == null ? Optional.empty() : retentionBond;
        materialsAllowance = materialsAllowance == null ? Optional.empty() : materialsAllowance;
        daywork = daywork == null ? Optional.empty() : daywork;
        completion = completion == null ? Optional.empty() : completion;
        defectsPeriodEnds = defectsPeriodEnds == null ? Optional.empty() : defectsPeriodEnds;
        outputAllowances = List.copyOf(outputAllowances);
        adjustment = adjustment == null ? Optional.empty() : adjustment;
        haulLinks = List.copyOf(haulLinks);
        workingDays = List.copyOf(workingDays);
        substantialCompletionOn = substantialCompletionOn == null ? Optional.empty() : substantialCompletionOn;
        defectsCertificateOn = defectsCertificateOn == null ? Optional.empty() : defectsCertificateOn;
        interimTerms = interimTerms == null ? InterimTerms.NONE : interimTerms;
    }

    /**
     * Settings with the contract defaults filled in: five percent retention, a Monday to Friday week,
     * a cut-off on the 25th, seven working days to issue and fourteen to pay, the standard tolerances,
     * no haulage cost, and none of the optional terms.
     *
     * @throws BadInputException for a cut-off that is not a day of the month, a negative contract sum,
     *     or a contract sum in another currency from the job
     */
    public static JobSettings build(JobSettingsInput input) {
        int cutOffDay = input.cutOffDay() == null ? DEFAULT_CUT_OFF_DAY : input.cutOffDay();
        if (cutOffDay < 1 || cutOffDay > LAST_DAY_OF_ANY_MONTH) {
            throw new BadInputException(
                    "the cut off day has to be a day of the month", Details.of("cutOffDay", cutOffDay));
        }
        if (input.contractSum().minor() < 0) {
            throw new BadInputException("the contract sum cannot be negative", Details.of("job", input.id()));
        }
        Currency currency = input.currency() == null ? input.contractSum().currency() : input.currency();
        if (currency != input.contractSum().currency()) {
            throw new BadInputException(
                    "the contract sum is in a different currency from the job",
                    Details.of(
                            "job",
                            currency.code(),
                            "contractSum",
                            input.contractSum().currency().code()));
        }
        return new JobSettings(
                input.id(),
                input.name(),
                currency,
                input.contractSum(),
                orElse(input.retention(), RetentionTerms.DEFAULT),
                Optional.ofNullable(input.retentionBond()),
                Optional.ofNullable(input.materialsAllowance()),
                Optional.ofNullable(input.daywork()),
                Optional.ofNullable(input.completion()),
                Optional.ofNullable(input.defectsPeriodEnds()),
                OutputAllowance.build(orElse(input.outputAllowances(), List.of())),
                Optional.ofNullable(input.adjustment()),
                orElse(input.contraNotifyDays(), Subcontract.DEFAULT_CONTRA_NOTIFY_DAYS),
                orElse(input.riskMeetingDays(), EarlyWarning.DEFAULT_MEETING_DAYS),
                orElse(input.haulRate(), Money.zero(currency)),
                orElse(input.haulLinks(), List.of()),
                orElse(input.tolerances(), ToleranceSet.DEFAULT),
                orElse(input.workingDays(), SiteCalendar.DEFAULT_WORKING_DAYS),
                cutOffDay,
                orElse(input.issueDays(), DEFAULT_ISSUE_DAYS),
                orElse(input.paymentDays(), DEFAULT_PAYMENT_DAYS),
                Optional.ofNullable(input.substantialCompletionOn()),
                Optional.ofNullable(input.defectsCertificateOn()),
                orElse(input.interimTerms(), InterimTerms.NONE));
    }

    private static <T> T orElse(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /** These settings with a bond in place of the cash retention, or with none when {@code other} is null. */
    public JobSettings withRetentionBond(RetentionBond other) {
        Draft draft = new Draft(this);
        draft.retentionBond = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withMaterialsAllowance(Percent other) {
        Draft draft = new Draft(this);
        draft.materialsAllowance = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withDaywork(DayworkSchedule other) {
        Draft draft = new Draft(this);
        draft.daywork = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withCompletion(CompletionTerms other) {
        Draft draft = new Draft(this);
        draft.completion = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withDefectsPeriodEnds(LocalDate other) {
        Draft draft = new Draft(this);
        draft.defectsPeriodEnds = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withOutputAllowances(List<OutputAllowance> other) {
        Draft draft = new Draft(this);
        draft.outputAllowances = other;
        return draft.settings();
    }

    public JobSettings withAdjustment(AdjustmentTerms other) {
        Draft draft = new Draft(this);
        draft.adjustment = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withHaulRate(Money other) {
        Draft draft = new Draft(this);
        draft.haulRate = other;
        return draft.settings();
    }

    public JobSettings withHaulLinks(List<HaulLink> other) {
        Draft draft = new Draft(this);
        draft.haulLinks = other;
        return draft.settings();
    }

    public JobSettings withWorkingDays(List<Integer> other) {
        Draft draft = new Draft(this);
        draft.workingDays = other;
        return draft.settings();
    }

    public JobSettings withSubstantialCompletionOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.substantialCompletionOn = Optional.ofNullable(other);
        return draft.settings();
    }

    public JobSettings withInterimTerms(InterimTerms other) {
        Draft draft = new Draft(this);
        draft.interimTerms = other;
        return draft.settings();
    }

    /** A copy of the settings with one field changed, so each wither names only its own field. */
    private static final class Draft {
        private final String id;
        private final String name;
        private final Currency currency;
        private final Money contractSum;
        private final RetentionTerms retention;
        private Optional<RetentionBond> retentionBond;
        private Optional<Percent> materialsAllowance;
        private Optional<DayworkSchedule> daywork;
        private Optional<CompletionTerms> completion;
        private Optional<LocalDate> defectsPeriodEnds;
        private List<OutputAllowance> outputAllowances;
        private Optional<AdjustmentTerms> adjustment;
        private final int contraNotifyDays;
        private final int riskMeetingDays;
        private Money haulRate;
        private List<HaulLink> haulLinks;
        private final ToleranceSet tolerances;
        private List<Integer> workingDays;
        private final int cutOffDay;
        private final int issueDays;
        private final int paymentDays;
        private Optional<LocalDate> substantialCompletionOn;
        private final Optional<LocalDate> defectsCertificateOn;
        private InterimTerms interimTerms;

        private Draft(JobSettings settings) {
            id = settings.id;
            name = settings.name;
            currency = settings.currency;
            contractSum = settings.contractSum;
            retention = settings.retention;
            retentionBond = settings.retentionBond;
            materialsAllowance = settings.materialsAllowance;
            daywork = settings.daywork;
            completion = settings.completion;
            defectsPeriodEnds = settings.defectsPeriodEnds;
            outputAllowances = settings.outputAllowances;
            adjustment = settings.adjustment;
            contraNotifyDays = settings.contraNotifyDays;
            riskMeetingDays = settings.riskMeetingDays;
            haulRate = settings.haulRate;
            haulLinks = settings.haulLinks;
            tolerances = settings.tolerances;
            workingDays = settings.workingDays;
            cutOffDay = settings.cutOffDay;
            issueDays = settings.issueDays;
            paymentDays = settings.paymentDays;
            substantialCompletionOn = settings.substantialCompletionOn;
            defectsCertificateOn = settings.defectsCertificateOn;
            interimTerms = settings.interimTerms;
        }

        private JobSettings settings() {
            return new JobSettings(
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
                    defectsCertificateOn,
                    interimTerms);
        }
    }
}
