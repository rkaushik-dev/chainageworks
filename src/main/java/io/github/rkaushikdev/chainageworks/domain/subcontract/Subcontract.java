package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A subcontract, and which of the main contract's work it covers.
 *
 * <p>The packages and the bill items are both recorded because they answer different questions.
 * The packages say where on the ground the subcontractor is working, which is what a site manager
 * needs. The items say what they are paid against, which is what the account needs, and the two are
 * not the same list as soon as one package is split between two trades.
 *
 * @param id the order's key
 * @param name what the order is for
 * @param contractor the subcontractor
 * @param orderValue the order value
 * @param packageIds the packages the subcontractor works in
 * @param codes the bill items it is paid against
 * @param terms the payment terms
 * @param placedOn the day the order was placed
 */
public record Subcontract(
        String id,
        String name,
        String contractor,
        Money orderValue,
        List<String> packageIds,
        List<ItemCode> codes,
        PaymentTerms terms,
        LocalDate placedOn) {

    /** Days to notify a contra charge that the order does not already cover. */
    public static final int DEFAULT_CONTRA_NOTIFY_DAYS = 14;

    public Subcontract {
        packageIds = List.copyOf(packageIds);
        codes = List.copyOf(codes);
    }

    /**
     * Everything a position is worked out from, so callers do not order five lists.
     *
     * @param applications every payment application
     * @param notices every payment notice
     * @param payLess every pay less notice
     * @param contra every contra charge
     * @param contraNotifyDays days to notify a contra charge the order does not already cover
     */
    public record Inputs(
            List<PaymentApplication> applications,
            List<PaymentNotice> notices,
            List<PayLessNotice> payLess,
            List<ContraCharge> contra,
            int contraNotifyDays) {

        public Inputs {
            applications = List.copyOf(applications);
            notices = List.copyOf(notices);
            payLess = List.copyOf(payLess);
            contra = List.copyOf(contra);
        }

        /** Applications and notices, with no contra charges. */
        public static Inputs of(
                List<PaymentApplication> applications, List<PaymentNotice> notices, List<PayLessNotice> payLess) {
            return new Inputs(applications, notices, payLess, List.of(), DEFAULT_CONTRA_NOTIFY_DAYS);
        }

        public Inputs withApplications(List<PaymentApplication> other) {
            return new Inputs(other, notices, payLess, contra, contraNotifyDays);
        }

        public Inputs withPayLess(List<PayLessNotice> other) {
            return new Inputs(applications, notices, other, contra, contraNotifyDays);
        }

        public Inputs withContra(List<ContraCharge> other) {
            return new Inputs(applications, notices, payLess, other, contraNotifyDays);
        }
    }

    /**
     * Where a subcontract stands at a date.
     *
     * @param subcontract the order
     * @param applications how many applications have been made by the date
     * @param certifiedToDate the latest certified figure, net of retention
     * @param paidToDate what has been paid across every application
     * @param remaining order value less what has been certified
     * @param defaulted applications that defaulted to the applied for sum
     * @param contra the contra charges standing against the order
     * @param netOfContra what actually leaves the bank once the set off is taken
     * @param contraCarriedForward set off that would not fit this time and waits for the next payment
     */
    public record Position(
            Subcontract subcontract,
            int applications,
            Money certifiedToDate,
            Money paidToDate,
            Money remaining,
            List<String> defaulted,
            ContraCharge.Position contra,
            Money netOfContra,
            Money contraCarriedForward) {

        public Position {
            defaulted = List.copyOf(defaulted);
        }

        /** {@code SC-01 Dowsett Plant Hire, 3 applications, inside the order}. */
        public String describe() {
            String state = remaining.minor() < 0 ? "over the order" : "inside the order";
            String setOff = contra.deductible().minor() == 0
                    ? ""
                    : ", " + Numbers.format((double) contra.deductible().minor() / 100) + " set off";
            return subcontract.id() + " " + subcontract.contractor() + ", " + applications + " applications, " + state
                    + setOff;
        }
    }

    /** Build an order, refusing a blank subcontractor, a negative value and one item listed twice. */
    public static Subcontract build(SubcontractInput input) {
        if (Text.trim(input.contractor()).isEmpty()) {
            throw new BadInputException(
                    "a subcontract has to name the subcontractor", Details.of("subcontract", input.id()));
        }
        if (input.orderValue().minor() < 0) {
            throw new BadInputException("an order value cannot be negative", Details.of("subcontract", input.id()));
        }
        Set<String> seen = new HashSet<>();
        for (ItemCode code : input.codes()) {
            if (!seen.add(code.key())) {
                throw new BadInputException(
                        "the same bill item is on one subcontract twice",
                        Details.of("subcontract", input.id(), "code", code.format()));
            }
        }
        return new Subcontract(
                input.id(),
                input.name(),
                Text.trim(input.contractor()),
                input.orderValue(),
                input.packageIds(),
                input.codes(),
                input.terms() == null ? PaymentTerms.build(PaymentTerms.DEFAULT) : input.terms(),
                input.placedOn());
    }

    public boolean coversPackage(String packageId) {
        return packageIds.contains(packageId);
    }

    public boolean coversCode(ItemCode code) {
        String key = code.key();
        return codes.stream().anyMatch(c -> c.key().equals(key));
    }

    /**
     * Where the subcontract stands at a date.
     *
     * <p>Certified and paid are kept apart because they are different facts: a certificate that
     * has been issued and not paid is a cash problem, and one that was never issued at all is a
     * notice problem. Reporting one figure hides which of the two the job has.
     */
    public Position position(Inputs inputs, LocalDate asAt) {
        Currency currency = orderValue.currency();
        List<PaymentApplication> mine = new ArrayList<>(inputs.applications().stream()
                .filter(a -> a.subcontractId().equals(id) && !a.appliedOn().isAfter(asAt))
                .toList());
        mine.sort(Comparator.comparingInt(PaymentApplication::periodNumber));

        List<Money> paid = new ArrayList<>();
        List<String> defaulted = new ArrayList<>();
        Money certified = Money.zero(currency);
        for (PaymentApplication application : mine) {
            PaymentTerms.Position position = terms.position(
                    application,
                    inputs.notices().stream()
                            .filter(n -> n.applicationId().equals(application.id()))
                            .findFirst(),
                    inputs.payLess().stream()
                            .filter(n -> n.applicationId().equals(application.id()))
                            .findFirst());
            certified = position.netOfRetention();
            paid.add(position.payable());
            if (position.byDefault()) {
                defaulted.add(application.id());
            }
        }

        Money paidToDate = Money.sum(paid, currency);
        ContraCharge.Position contra =
                ContraCharge.position(inputs.contra(), id, asAt, inputs.contraNotifyDays(), currency);
        return new Position(
                this,
                mine.size(),
                certified,
                paidToDate,
                orderValue.minus(certified),
                defaulted,
                contra,
                contra.applyTo(paidToDate),
                contra.carriedForward(paidToDate));
    }

    public static Optional<Subcontract> find(List<Subcontract> subcontracts, String id) {
        return subcontracts.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public static Subcontract require(List<Subcontract> subcontracts, String id) {
        return find(subcontracts, id).orElseThrow(() -> new NotFoundException("subcontract", id));
    }

    /** Subcontracts whose certified total has run past the order value. */
    public static List<Position> overOrder(List<Position> positions) {
        return positions.stream().filter(p -> p.remaining().minor() < 0).toList();
    }
}
