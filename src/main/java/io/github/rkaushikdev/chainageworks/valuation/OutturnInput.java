package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSchedule;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import java.time.LocalDate;
import java.util.List;

/**
 * What the anticipated final account is rolled up from.
 *
 * @param context the read model of the job
 * @param asAt the date the account is taken at
 * @param contractSum the tendered contract sum the movement is measured against
 * @param variations the variation register; null means none
 * @param dayworkSchedule the daywork schedule; null when the contract has none
 * @param dayworkSheets the daywork sheets; null means none
 * @param adjustment the fluctuation clause; null when the contract carries none
 * @param indices the index series the clause reads; null means none
 * @param completion the completion date and damages; null when the contract fixes none
 * @param delayEvents the delay events; null means none
 * @param calendar the site calendar the extensions are counted on; null leaves damages out
 * @param trendThreshold where the measured works switch from the bill to the trend; null means
 *     {@link FinalAccount#DEFAULT_TREND_THRESHOLD}
 */
public record OutturnInput(
        ProgressContext context,
        LocalDate asAt,
        Money contractSum,
        List<Variation> variations,
        DayworkSchedule dayworkSchedule,
        List<DayworkSheet> dayworkSheets,
        AdjustmentTerms adjustment,
        List<IndexSeries> indices,
        CompletionTerms completion,
        List<DelayEvent> delayEvents,
        SiteCalendar calendar,
        Percent trendThreshold) {

    public OutturnInput {
        variations = variations == null ? List.of() : List.copyOf(variations);
        dayworkSheets = dayworkSheets == null ? List.of() : List.copyOf(dayworkSheets);
        indices = indices == null ? List.of() : List.copyOf(indices);
        delayEvents = delayEvents == null ? List.of() : List.copyOf(delayEvents);
    }

    /** The measured works on their own, with none of the other parts. */
    public static OutturnInput of(ProgressContext context, LocalDate asAt, Money contractSum) {
        return new OutturnInput(context, asAt, contractSum, null, null, null, null, null, null, null, null, null);
    }

    public OutturnInput withVariations(List<Variation> other) {
        return new OutturnInput(
                context,
                asAt,
                contractSum,
                other,
                dayworkSchedule,
                dayworkSheets,
                adjustment,
                indices,
                completion,
                delayEvents,
                calendar,
                trendThreshold);
    }

    /** The daywork schedule and the sheets valued against it. */
    public OutturnInput withDaywork(DayworkSchedule schedule, List<DayworkSheet> sheets) {
        return new OutturnInput(
                context,
                asAt,
                contractSum,
                variations,
                schedule,
                sheets,
                adjustment,
                indices,
                completion,
                delayEvents,
                calendar,
                trendThreshold);
    }

    /** The fluctuation clause and the index series it reads. */
    public OutturnInput withAdjustment(AdjustmentTerms terms, List<IndexSeries> series) {
        return new OutturnInput(
                context,
                asAt,
                contractSum,
                variations,
                dayworkSchedule,
                dayworkSheets,
                terms,
                series,
                completion,
                delayEvents,
                calendar,
                trendThreshold);
    }

    /** The completion terms and the site calendar the extensions are counted on. */
    public OutturnInput withCompletion(CompletionTerms terms, SiteCalendar siteCalendar) {
        return new OutturnInput(
                context,
                asAt,
                contractSum,
                variations,
                dayworkSchedule,
                dayworkSheets,
                adjustment,
                indices,
                terms,
                delayEvents,
                siteCalendar,
                trendThreshold);
    }

    public OutturnInput withDelayEvents(List<DelayEvent> other) {
        return new OutturnInput(
                context,
                asAt,
                contractSum,
                variations,
                dayworkSchedule,
                dayworkSheets,
                adjustment,
                indices,
                completion,
                other,
                calendar,
                trendThreshold);
    }

    public OutturnInput withTrendThreshold(Percent other) {
        return new OutturnInput(
                context,
                asAt,
                contractSum,
                variations,
                dayworkSchedule,
                dayworkSheets,
                adjustment,
                indices,
                completion,
                delayEvents,
                calendar,
                other);
    }
}
