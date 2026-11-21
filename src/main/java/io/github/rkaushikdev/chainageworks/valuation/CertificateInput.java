package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
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
        LocalDate defectsCertificateOn) {

    public CertificateInput {
        materials = materials == null ? List.of() : List.copyOf(materials);
        dayworkSheets = dayworkSheets == null ? List.of() : List.copyOf(dayworkSheets);
        indices = indices == null ? List.of() : List.copyOf(indices);
    }

    /** A fixed price contract with no materials, no daywork and no bond. */
    public static CertificateInput of(ProgressContext context, Money contractSum, RetentionTerms retention) {
        return new CertificateInput(
                context, contractSum, retention, null, null, null, null, null, null, null, null, null);
    }

    public CertificateInput withMaterials(List<MaterialsOnSite> other) {
        return new CertificateInput(
                context,
                contractSum,
                retention,
                other,
                materialsAllowance,
                dayworkSchedule,
                dayworkSheets,
                adjustment,
                indices,
                bond,
                substantialCompletionOn,
                defectsCertificateOn);
    }

    public CertificateInput withMaterialsAllowance(Percent other) {
        return new CertificateInput(
                context,
                contractSum,
                retention,
                materials,
                other,
                dayworkSchedule,
                dayworkSheets,
                adjustment,
                indices,
                bond,
                substantialCompletionOn,
                defectsCertificateOn);
    }

    /** The daywork schedule and the sheets valued against it. */
    public CertificateInput withDaywork(DayworkSchedule schedule, List<DayworkSheet> sheets) {
        return new CertificateInput(
                context,
                contractSum,
                retention,
                materials,
                materialsAllowance,
                schedule,
                sheets,
                adjustment,
                indices,
                bond,
                substantialCompletionOn,
                defectsCertificateOn);
    }

    /** The fluctuation clause and the index series it reads. */
    public CertificateInput withAdjustment(AdjustmentTerms terms, List<IndexSeries> series) {
        return new CertificateInput(
                context,
                contractSum,
                retention,
                materials,
                materialsAllowance,
                dayworkSchedule,
                dayworkSheets,
                terms,
                series,
                bond,
                substantialCompletionOn,
                defectsCertificateOn);
    }

    public CertificateInput withBond(RetentionBond other) {
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
                other,
                substantialCompletionOn,
                defectsCertificateOn);
    }

    public CertificateInput withSubstantialCompletionOn(LocalDate other) {
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
                other,
                defectsCertificateOn);
    }

    public CertificateInput withDefectsCertificateOn(LocalDate other) {
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
                other);
    }
}
