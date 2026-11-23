package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import java.time.LocalDate;
import java.util.List;

/**
 * What an interim certificate is worked out from, apart from the period it values and the figure
 * the previous certificate reached. The same input values every period of a series.
 *
 * @param context the read model of the job
 * @param contractSum the tendered contract sum, which the retention cap is set against
 * @param retention the retention terms
 * @param materials the unfixed materials declared on site; null means none
 * @param materialsAllowance the share of their invoice value certified; null means {@link
 *     MaterialsOnSite#DEFAULT_ALLOWANCE}
 * @param dayworkSchedule the daywork schedule; null when the contract has none
 * @param dayworkSheets the daywork sheets; null means none
 * @param adjustment the fluctuation clause; null when the contract carries none
 * @param indices the index series the clause reads; null means none
 * @param bond a retention bond standing in place of the cash; null when there is none
 * @param substantialCompletionOn when the works were taken over; null when they have not been
 * @param defectsCertificateOn when the defects certificate was issued; null when it has not been
 * @param completion the completion date and damages; null when the contract fixes none, and then
 *     nothing is ever deducted for lateness
 * @param delayEvents the delay events the completion date is extended by; null means none
 */
public record CertificateInput(
        ProgressContext context,
        Money contractSum,
        RetentionTerms retention,
        List<MaterialsOnSite> materials,
        Percent materialsAllowance,
        DayworkSchedule dayworkSchedule,
        List<DayworkSheet> dayworkSheets,
        AdjustmentTerms adjustment,
        List<IndexSeries> indices,
        RetentionBond bond,
        LocalDate substantialCompletionOn,
        LocalDate defectsCertificateOn,
        CompletionTerms completion,
        List<DelayEvent> delayEvents) {

    public CertificateInput {
        materials = materials == null ? List.of() : List.copyOf(materials);
        dayworkSheets = dayworkSheets == null ? List.of() : List.copyOf(dayworkSheets);
        indices = indices == null ? List.of() : List.copyOf(indices);
        delayEvents = delayEvents == null ? List.of() : List.copyOf(delayEvents);
    }

    /** A fixed price contract with no materials, no daywork and no bond. */
    public static CertificateInput of(ProgressContext context, Money contractSum, RetentionTerms retention) {
        return new CertificateInput(
                context, contractSum, retention, null, null, null, null, null, null, null, null, null, null, null);
    }

    public CertificateInput withMaterials(List<MaterialsOnSite> other) {
        Draft draft = new Draft(this);
        draft.materials = other;
        return draft.input();
    }

    public CertificateInput withMaterialsAllowance(Percent other) {
        Draft draft = new Draft(this);
        draft.materialsAllowance = other;
        return draft.input();
    }

    /** The daywork schedule and the sheets valued against it. */
    public CertificateInput withDaywork(DayworkSchedule schedule, List<DayworkSheet> sheets) {
        Draft draft = new Draft(this);
        draft.dayworkSchedule = schedule;
        draft.dayworkSheets = sheets;
        return draft.input();
    }

    /** The fluctuation clause and the index series it reads. */
    public CertificateInput withAdjustment(AdjustmentTerms terms, List<IndexSeries> series) {
        Draft draft = new Draft(this);
        draft.adjustment = terms;
        draft.indices = series;
        return draft.input();
    }

    public CertificateInput withBond(RetentionBond other) {
        Draft draft = new Draft(this);
        draft.bond = other;
        return draft.input();
    }

    public CertificateInput withSubstantialCompletionOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.substantialCompletionOn = other;
        return draft.input();
    }

    public CertificateInput withDefectsCertificateOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.defectsCertificateOn = other;
        return draft.input();
    }

    /** The completion terms and the delay events that extend them, for the damages deduction. */
    public CertificateInput withCompletion(CompletionTerms terms, List<DelayEvent> events) {
        Draft draft = new Draft(this);
        draft.completion = terms;
        draft.delayEvents = events;
        return draft.input();
    }

    /** A copy of the input with some parts changed, so each wither names only its own parts. */
    private static final class Draft {
        private final ProgressContext context;
        private final Money contractSum;
        private final RetentionTerms retention;
        private List<MaterialsOnSite> materials;
        private Percent materialsAllowance;
        private DayworkSchedule dayworkSchedule;
        private List<DayworkSheet> dayworkSheets;
        private AdjustmentTerms adjustment;
        private List<IndexSeries> indices;
        private RetentionBond bond;
        private LocalDate substantialCompletionOn;
        private LocalDate defectsCertificateOn;
        private CompletionTerms completion;
        private List<DelayEvent> delayEvents;

        private Draft(CertificateInput input) {
            context = input.context;
            contractSum = input.contractSum;
            retention = input.retention;
            materials = input.materials;
            materialsAllowance = input.materialsAllowance;
            dayworkSchedule = input.dayworkSchedule;
            dayworkSheets = input.dayworkSheets;
            adjustment = input.adjustment;
            indices = input.indices;
            bond = input.bond;
            substantialCompletionOn = input.substantialCompletionOn;
            defectsCertificateOn = input.defectsCertificateOn;
            completion = input.completion;
            delayEvents = input.delayEvents;
        }

        private CertificateInput input() {
            return new CertificateInput(
                    context,
                    contractSum,
                    retention,
                    materials,
                    materialsAllowance,
                    dayworkSchedule,
                    dayworkSheets,
                    adjustment,
                    indices,
                    bond,
                    substantialCompletionOn,
                    defectsCertificateOn,
                    completion,
                    delayEvents);
        }
    }
}
