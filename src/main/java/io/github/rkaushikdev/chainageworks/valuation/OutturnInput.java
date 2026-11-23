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
 * @param takenOverOn when the works were taken over, which is where damages stop running; null
 *     while they have not been
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
        Percent trendThreshold,
        LocalDate takenOverOn) {

    public OutturnInput {
        variations = variations == null ? List.of() : List.copyOf(variations);
        dayworkSheets = dayworkSheets == null ? List.of() : List.copyOf(dayworkSheets);
        indices = indices == null ? List.of() : List.copyOf(indices);
        delayEvents = delayEvents == null ? List.of() : List.copyOf(delayEvents);
    }

    /** The measured works on their own, with none of the other parts. */
    public static OutturnInput of(ProgressContext context, LocalDate asAt, Money contractSum) {
        return new OutturnInput(context, asAt, contractSum, null, null, null, null, null, null, null, null, null, null);
    }

    public OutturnInput withVariations(List<Variation> other) {
        Draft draft = new Draft(this);
        draft.variations = other;
        return draft.input();
    }

    /** The daywork schedule and the sheets valued against it. */
    public OutturnInput withDaywork(DayworkSchedule schedule, List<DayworkSheet> sheets) {
        Draft draft = new Draft(this);
        draft.dayworkSchedule = schedule;
        draft.dayworkSheets = sheets;
        return draft.input();
    }

    /** The fluctuation clause and the index series it reads. */
    public OutturnInput withAdjustment(AdjustmentTerms terms, List<IndexSeries> series) {
        Draft draft = new Draft(this);
        draft.adjustment = terms;
        draft.indices = series;
        return draft.input();
    }

    /** The completion terms and the site calendar the extensions are counted on. */
    public OutturnInput withCompletion(CompletionTerms terms, SiteCalendar siteCalendar) {
        Draft draft = new Draft(this);
        draft.completion = terms;
        draft.calendar = siteCalendar;
        return draft.input();
    }

    public OutturnInput withDelayEvents(List<DelayEvent> other) {
        Draft draft = new Draft(this);
        draft.delayEvents = other;
        return draft.input();
    }

    public OutturnInput withTrendThreshold(Percent other) {
        Draft draft = new Draft(this);
        draft.trendThreshold = other;
        return draft.input();
    }

    /** The day the works were taken over, after which no more damages accrue. */
    public OutturnInput withTakenOverOn(LocalDate other) {
        Draft draft = new Draft(this);
        draft.takenOverOn = other;
        return draft.input();
    }

    /** A copy of the input with some parts changed, so each wither names only its own parts. */
    private static final class Draft {
        private final ProgressContext context;
        private final LocalDate asAt;
        private final Money contractSum;
        private List<Variation> variations;
        private DayworkSchedule dayworkSchedule;
        private List<DayworkSheet> dayworkSheets;
        private AdjustmentTerms adjustment;
        private List<IndexSeries> indices;
        private CompletionTerms completion;
        private List<DelayEvent> delayEvents;
        private SiteCalendar calendar;
        private Percent trendThreshold;
        private LocalDate takenOverOn;

        private Draft(OutturnInput input) {
            context = input.context;
            asAt = input.asAt;
            contractSum = input.contractSum;
            variations = input.variations;
            dayworkSchedule = input.dayworkSchedule;
            dayworkSheets = input.dayworkSheets;
            adjustment = input.adjustment;
            indices = input.indices;
            completion = input.completion;
            delayEvents = input.delayEvents;
            calendar = input.calendar;
            trendThreshold = input.trendThreshold;
            takenOverOn = input.takenOverOn;
        }

        private OutturnInput input() {
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
                    trendThreshold,
                    takenOverOn);
        }
    }
}
