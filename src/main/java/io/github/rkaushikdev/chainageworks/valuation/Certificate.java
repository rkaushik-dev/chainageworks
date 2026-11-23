package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.PriceAdjustment;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An interim payment certificate.
 *
 * <p>Everything on it is cumulative except the amount due, which is the only figure that is a
 * difference. That is the whole shape of interim payment: each certificate values the works from
 * the start and pays the movement, so a mistake in one month corrects itself in the next rather than
 * compounding.
 *
 * @param period the valuation period it certifies
 * @param billRevision the reference of the bill revision it was priced against
 * @param earnings what each package earned, for the backing sheets
 * @param measuredWork the packages' earnings added up
 * @param materialsOnSite the certified value of unfixed materials, after the allowance and the item caps
 * @param materialsClaimed the lots of materials that met the conditions, before any item was capped
 * @param materialsCapped the bill items whose materials were held down to what the item has left to
 *     earn
 * @param daywork signed daywork, which is measured work that could not be measured
 * @param priceAdjustment what the fluctuation clause added to, or took off, the permanent work
 * @param gross the gross valuation
 * @param retention the retention account at the cut-off
 * @param bond where the bond stands against the cash, empty when there is no bond
 * @param retentionHeld the retention actually held in cash this month, after the bond
 * @param damages liquidated damages deducted to date, nothing when the job is not late or the
 *     contract fixes no completion date
 * @param advancePaid the advance payment standing on the account, nothing before it was paid
 * @param advanceRepaid what the certificates have deducted towards repaying the advance to date
 * @param netValuation the gross less the retention held and the damages, with the advance still
 *     outstanding added back, which is what has been certified to date
 * @param previouslyCertified the figure the previous certificate certified to date
 * @param heldOver true when the month moved the account by less than the minimum certificate, so
 *     nothing is certified and the movement waits for the next month
 * @param amountDue what this certificate pays: the net valuation less the previous figure, or
 *     nothing when it is held over
 */
public record Certificate(
        ValuationPeriod period,
        String billRevision,
        List<PackageEarning> earnings,
        Money measuredWork,
        Money materialsOnSite,
        List<MaterialsOnSite> materialsClaimed,
        List<ItemCode> materialsCapped,
        Money daywork,
        PriceAdjustment priceAdjustment,
        Money gross,
        RetentionState retention,
        Optional<BondPosition> bond,
        Money retentionHeld,
        Money damages,
        Money advancePaid,
        Money advanceRepaid,
        Money netValuation,
        Money previouslyCertified,
        boolean heldOver,
        Money amountDue) {

    public Certificate {
        earnings = List.copyOf(earnings);
        materialsClaimed = List.copyOf(materialsClaimed);
        materialsCapped = List.copyOf(materialsCapped);
        bond = bond == null ? Optional.empty() : bond;
    }

    /**
     * One line of the certificate as it is printed.
     *
     * @param name what the line is
     * @param amount the figure on it
     * @param total true for the lines a reader is meant to stop at
     */
    public record Line(String name, Money amount, boolean total) {}

    /**
     * Value the works at the period's cut-off and pay the movement since the previous certificate.
     *
     * @param previouslyCertified the cumulative net figure the previous certificate reached
     * @throws BadInputException when the previous figure is in another currency from the bill
     */
    public static Certificate build(CertificateInput input, ValuationPeriod period, Money previouslyCertified) {
        return build(input, period, previouslyCertified, Optional.empty());
    }

    /**
     * Value the works at the period's cut-off following on from the previous certificate, which a
     * clause that adjusts each period's work needs as well as the net figure.
     *
     * @param previous the previous certificate in the series, empty for the first
     */
    public static Certificate following(
            CertificateInput input, ValuationPeriod period, Optional<Certificate> previous) {
        Money previouslyCertified = previous.map(Certificate::certifiedToDate)
                .orElse(Money.zero(input.contractSum().currency()));
        return build(input, period, previouslyCertified, previous.map(Certificate::priceAdjustment));
    }

    private static Certificate build(
            CertificateInput input,
            ValuationPeriod period,
            Money previouslyCertified,
            Optional<PriceAdjustment> previousAdjustment) {
        LocalDate asAt = period.cutOff();
        Bill bill = input.context().billHistory().billAsAt(asAt);
        Currency currency = bill.currency();
        if (previouslyCertified.currency() != currency) {
            throw new BadInputException(
                    "the previous certificate is in another currency",
                    Details.of("bill", currency, "previous", previouslyCertified.currency()));
        }

        List<PackageEarning> earnings = PackageEarning.earnAll(input.context(), asAt);
        Money measuredWork =
                Money.sum(earnings.stream().map(PackageEarning::earnedValue).toList(), currency);
        MaterialsOnSite.Claim materials = materialsAt(input, bill, earnings, asAt);
        Money materialsOnSite = materials.value();
        Money daywork = input.dayworkSchedule() == null
                ? Money.zero(currency)
                : DayworkSheet.dayworkValue(input.dayworkSchedule(), input.dayworkSheets(), asAt);

        // The formula moves the permanent work and nothing else. Materials on site are left out on
        // purpose: they are paid at what the supplier invoiced, which already carries whatever the
        // market did, and running them through an index as well pays the same movement twice.
        Money permanentWork = measuredWork.plus(daywork);
        Optional<CompletionTerms.Position> completion = Optional.ofNullable(input.completion())
                .map(terms ->
                        terms.position(input.delayEvents(), input.context().calendar(), asAt));
        PriceAdjustment adjustment = input.adjustment() == null
                ? PriceAdjustment.none(permanentWork, asAt)
                : input.adjustment()
                        .adjustFrom(
                                previousAdjustment,
                                input.indices(),
                                permanentWork,
                                asAt,
                                completion.map(CompletionTerms.Position::extendedCompletion));

        Money gross = measuredWork.plus(materialsOnSite).plus(daywork).plus(adjustment.amount());

        RetentionState retention = RetentionState.compute(new RetentionQuery(
                gross,
                input.contractSum(),
                input.retention(),
                input.substantialCompletionOn(),
                input.defectsCertificateOn(),
                asAt));

        // A bond does not change what the retention is, only whether it is held in cash. Worked out
        // this way round, the retention account still releases the same money on the same dates at
        // the end of the job, and a bond that expires early puts the cash back in the employer's
        // hands without anything else on the certificate moving.
        Optional<BondPosition> position = Optional.ofNullable(input.bond())
                .map(bond -> bond.position(retention.held(), asAt, input.substantialCompletionOn()));
        Money retentionHeld = position.map(BondPosition::shortfall).orElse(retention.held());
        Money damages =
                completion.map(standing -> damagesAt(input, standing, asAt)).orElse(Money.zero(currency));
        Optional<AdvancePayment> advance = input.interimTerms().advance();
        Money advancePaid = advance.map(a -> a.paidAt(asAt)).orElse(Money.zero(currency));
        Money advanceRepaid =
                advance.map(a -> a.repaidAt(gross, input.contractSum(), asAt)).orElse(Money.zero(currency));
        Money netValuation =
                gross.minus(retentionHeld).minus(damages).plus(advancePaid).minus(advanceRepaid);

        Money due = netValuation.minus(previouslyCertified);
        boolean heldOver = input.interimTerms().holdsOver(due);
        return new Certificate(
                period,
                input.context().billHistory().asAt(asAt).reference(),
                earnings,
                measuredWork,
                materialsOnSite,
                materials.lots(),
                materials.capped(),
                daywork,
                adjustment,
                gross,
                retention,
                position,
                retentionHeld,
                damages,
                advancePaid,
                advanceRepaid,
                netValuation,
                previouslyCertified,
                heldOver,
                heldOver ? Money.zero(currency) : due);
    }

    /**
     * Damages to date, deducted on the certificate rather than left for the final account.
     *
     * <p>Cumulative like every other figure on it, so the month the job goes late pays the
     * deduction to date and each month after pays only what accrued since. They stop on the day the
     * works are taken over, which is also the day nothing more is owed.
     */
    /** The unfixed materials at the contract allowance, each item held to what it has left to earn. */
    private static MaterialsOnSite.Claim materialsAt(
            CertificateInput input, Bill bill, List<PackageEarning> earnings, LocalDate asAt) {
        Percent allowance =
                input.materialsAllowance() == null ? MaterialsOnSite.DEFAULT_ALLOWANCE : input.materialsAllowance();
        return MaterialsOnSite.claim(input.materials(), asAt, bill.currency(), allowance, headroom(bill, earnings));
    }

    /**
     * What each earnable bill item has left to earn: its billed value less what the packages have
     * earned against it so far. Provisional items are left out, because nothing may be claimed
     * against them at all until they are instructed.
     */
    private static Map<String, Money> headroom(Bill bill, List<PackageEarning> earnings) {
        Map<String, Money> earned = new HashMap<>();
        for (PackageEarning earning : earnings) {
            for (PackageEarning.Allocation allocation : earning.allocations()) {
                earned.merge(allocation.code().key(), allocation.earnedValue(), Money::plus);
            }
        }
        Map<String, Money> headroom = new HashMap<>();
        for (BillItem item : bill.items()) {
            if (item.isEarnable()) {
                Money done = earned.getOrDefault(item.code().key(), Money.zero(bill.currency()));
                headroom.put(item.code().key(), item.billedValue().minus(done));
            }
        }
        return headroom;
    }

    private static Money damagesAt(CertificateInput input, CompletionTerms.Position position, LocalDate asAt) {
        return input.completion()
                .damages(position, input.contractSum(), asAt, Optional.ofNullable(input.substantialCompletionOn()))
                .amount();
    }

    /**
     * Run a whole schedule, feeding each certificate the last one's net figure. The first is
     * certified against nothing.
     */
    public static List<Certificate> series(List<ValuationPeriod> periods, CertificateInput input) {
        List<Certificate> out = new ArrayList<>(periods.size());
        for (ValuationPeriod period : periods) {
            out.add(following(input, period, out.isEmpty() ? Optional.empty() : Optional.of(out.get(out.size() - 1))));
        }
        return List.copyOf(out);
    }

    /** True when this month values less than the last one did. */
    public boolean isNegative() {
        return amountDue.minor() < 0;
    }

    /**
     * What has been certified to date: the net valuation, or the previous figure again when this
     * month is held over below the minimum.
     */
    public Money certifiedToDate() {
        return heldOver ? previouslyCertified : netValuation;
    }

    /** True when the certificate rests on an index figure that may still move. */
    public boolean restsOnProvisionalIndex() {
        return priceAdjustment.provisional();
    }

    /** Certificates in a series that will have to be reworked when indices firm up. */
    public static List<Certificate> provisional(List<Certificate> series) {
        return series.stream().filter(Certificate::restsOnProvisionalIndex).toList();
    }

    /** Cash the bond is keeping out of the employer's hands this month. */
    public Money bondReleased() {
        return bond.map(BondPosition::released).orElse(Money.zero(gross.currency()));
    }

    /** True when the bond has lapsed and the cash retention has come back. */
    public boolean bondHasLapsed() {
        return bond.map(BondPosition::expired).orElse(false);
    }

    /**
     * The certificate as an ordered list of lines.
     *
     * <p>One list, four readers: the terminal, the markdown pack, the API and the spreadsheet
     * export. Each having its own copy is how three readers end up with three orderings, which is
     * exactly the sort of difference nobody spots until a client compares the PDF against the CSV.
     *
     * <p>The optional lines are left out rather than printed as zero. A price adjustment row reading
     * nothing invites the question of which index produced it, and on a fixed price contract the
     * answer is that there is no clause.
     */
    public List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("measured work", measuredWork, false));
        lines.add(new Line("materials on site", materialsOnSite, false));
        lines.add(new Line("daywork", daywork, false));

        if (!priceAdjustment.factor().lines().isEmpty()) {
            lines.add(new Line(
                    "price adjustment at "
                            + Numbers.toFixed(priceAdjustment.factor().factor() * 100, 3) + "%",
                    priceAdjustment.amount(),
                    false));
        }

        lines.add(new Line("gross valuation", gross, true));

        bond.ifPresent(position -> {
            lines.add(new Line("retention due", retention.held(), false));
            lines.add(new Line("covered by bond " + position.bond().id(), position.released(), false));
        });

        lines.add(new Line("less retention", retentionHeld, false));
        if (!damages.isZero()) {
            lines.add(new Line("less liquidated damages", damages, false));
        }
        if (!advancePaid.isZero()) {
            lines.add(new Line("advance payment", advancePaid, false));
            lines.add(new Line("less advance repaid", advanceRepaid, false));
        }
        lines.add(new Line("net valuation", netValuation, true));
        lines.add(new Line("less previously certified", previouslyCertified, false));
        if (heldOver) {
            lines.add(new Line(
                    "held over below the minimum certificate", netValuation.minus(previouslyCertified), false));
        }
        lines.add(new Line("amount due this certificate", amountDue, true));
        return List.copyOf(lines);
    }

    /** What the certificate warns about, in the order a reader meets it. */
    public List<String> notes() {
        List<String> notes = new ArrayList<>();
        if (heldOver) {
            notes.add("the movement is below the minimum certificate and is held over to next month");
        }
        for (ItemCode code : materialsCapped) {
            notes.add("materials for " + code.format() + " are held to what the item has left to earn");
        }
        if (retention.capReached()) {
            notes.add("retention has reached its cap");
        }
        if (priceAdjustment.provisional()) {
            notes.add("the price adjustment rests on a provisional index figure");
        }
        if (!damages.isZero()) {
            notes.add("liquidated damages are being deducted for late completion");
        }
        if (!advancePaid.isZero() && advanceRepaid.equals(advancePaid)) {
            notes.add("the advance payment has been repaid in full");
        }
        if (priceAdjustment.heldAtCompletion()) {
            notes.add("the price adjustment is held at the completion date because the works are late");
        }
        if (bondHasLapsed()) {
            notes.add("the retention bond has lapsed and the cash is being held again");
        }
        return List.copyOf(notes);
    }
}
