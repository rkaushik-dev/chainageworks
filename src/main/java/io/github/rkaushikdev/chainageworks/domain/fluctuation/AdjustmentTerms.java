package io.github.rkaushikdev.chainageworks.domain.fluctuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The contract price adjustment clause: the base date, the fixed element and the index weightings.
 *
 * @param baseDate the month the tender was priced in, which every index is read against
 * @param nonAdjustable the share of the price that never moves, however the indices do
 * @param weightings the indices and their shares
 * @param lagMonths how many months the current figures trail the valuation by. Most clauses read
 *     the index for a month or two before the one being valued, because that is the newest figure
 *     anybody can have when the certificate is due
 */
public record AdjustmentTerms(LocalDate baseDate, Percent nonAdjustable, List<Weighting> weightings, int lagMonths) {

    public AdjustmentTerms {
        weightings = List.copyOf(weightings);
    }

    /**
     * Build the terms. The weightings and the fixed element have to come to the whole price.
     *
     * <p>Checked rather than normalised. A set that comes to ninety eight percent is a typing
     * mistake in a contract document, and quietly scaling it up would make the adjustment slightly
     * wrong every month for the life of the job with nothing anywhere to say why.
     *
     * @param nonAdjustable the fixed element, or null for none
     */
    public static AdjustmentTerms build(LocalDate baseDate, Percent nonAdjustable, List<Weighting> weightings) {
        return build(baseDate, nonAdjustable, weightings, 0);
    }

    /**
     * Build the terms with the current figures read {@code lagMonths} months behind the valuation.
     *
     * @throws BadInputException for a negative lag, besides everything the shorter form refuses
     */
    public static AdjustmentTerms build(
            LocalDate baseDate, Percent nonAdjustable, List<Weighting> weightings, int lagMonths) {
        if (lagMonths < 0) {
            throw new BadInputException(
                    "an index lag is a whole number of months back", Details.of("lagMonths", lagMonths));
        }
        Percent fixed = nonAdjustable == null ? Percent.of(0) : nonAdjustable;
        Set<String> seen = new HashSet<>();
        long total = fixed.basisPoints();
        for (Weighting weighting : weightings) {
            if (seen.contains(weighting.indexId())) {
                throw new BadInputException(
                        "an index carries two weightings", Details.of("index", weighting.indexId()));
            }
            if (weighting.weight().basisPoints() < 0) {
                throw new BadInputException("a weighting cannot be negative", Details.of("index", weighting.indexId()));
            }
            seen.add(weighting.indexId());
            total += weighting.weight().basisPoints();
        }
        if (total != Percent.BASIS_POINTS_WHOLE) {
            throw new BadInputException(
                    "the weightings and the fixed element have to come to 100", Details.of("total", total / 100.0));
        }
        return new AdjustmentTerms(baseDate, fixed, weightings, lagMonths);
    }

    /**
     * Work out the price adjustment factor at a date.
     *
     * <p>The fixed element takes no part in the sum at all rather than contributing zero movement,
     * because those two are only the same while the arithmetic is right, and the difference shows
     * up as a factor that is quietly too small when a weighting is missing.
     */
    public AdjustmentFactor factorAt(List<IndexSeries> series, LocalDate asAt) {
        LocalDate readAt = indicesReadAt(asAt);
        List<AdjustmentFactor.Line> lines = weightings.stream()
                .map(weighting -> {
                    IndexSeries found = IndexSeries.require(series, weighting.indexId());
                    double movement = found.movementSince(baseDate, readAt);
                    return new AdjustmentFactor.Line(
                            weighting.indexId(),
                            weighting.weight(),
                            movement,
                            weighting.weight().fraction() * movement,
                            found.isProvisionalAt(readAt) || found.isProvisionalAt(baseDate));
                })
                .toList();
        double factor =
                lines.stream().mapToDouble(AdjustmentFactor.Line::contribution).sum();
        return new AdjustmentFactor(
                asAt, readAt, lines, factor, lines.stream().anyMatch(AdjustmentFactor.Line::provisional));
    }

    /**
     * The date the current figures are read for at a valuation: the lag back from it, and never
     * earlier than the base date. A valuation that falls inside the lag of the base date has not
     * moved at all yet, which is what the clause means, not an index read before the tender.
     */
    public LocalDate indicesReadAt(LocalDate asAt) {
        return SiteDates.max(SiteDates.addMonths(asAt, -lagMonths), baseDate);
    }

    /** The same clause with the current figures read {@code months} back. */
    public AdjustmentTerms withLagMonths(int months) {
        return build(baseDate, nonAdjustable, weightings, months);
    }

    /**
     * What the formula adds to, or takes off, a valuation.
     *
     * <p>The fixed element is taken off the base before the factor is applied rather than being
     * folded into the factor. Both give the same number when everything is right, but only this
     * order can answer the question a payer always asks, which is how much of the certificate the
     * formula was allowed to touch.
     */
    public PriceAdjustment adjust(List<IndexSeries> series, Money base, LocalDate asAt) {
        AdjustmentFactor factor = factorAt(series, asAt);
        Money adjustable = base.scale(1 - nonAdjustable.fraction());
        return new PriceAdjustment(
                asAt, base, adjustable, factor, adjustable.scale(factor.factor()), factor.provisional());
    }
}
