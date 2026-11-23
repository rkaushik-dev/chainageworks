package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The payment terms of a subcontract.
 *
 * <p>The two notice periods are what the whole thing turns on. The payer has so many days from the
 * due date to say what it thinks the work is worth, and so many more to say it intends to pay less.
 * Miss either and the amount applied for becomes the amount payable, whatever anybody thinks the
 * work was worth.
 *
 * @param dueDays days from the application to the payment falling due
 * @param noticeDays days from the due date to serve the payment notice
 * @param payLessDays days before the final date for payment to serve a pay less notice
 * @param finalDays days from the due date to the final date for payment
 * @param retention the retention held on each payment
 * @param retentionLimit the most retention the order allows to be held, as a share of the order
 *     value, empty when the rate runs on uncapped
 */
public record PaymentTerms(
        int dueDays,
        int noticeDays,
        int payLessDays,
        int finalDays,
        Percent retention,
        Optional<Percent> retentionLimit) {

    /** The usual statutory windows and five percent retention. */
    public static final PaymentTerms DEFAULT = new PaymentTerms(30, 5, 7, 45, Percent.of(5));

    public PaymentTerms {
        retentionLimit = retentionLimit == null ? Optional.empty() : retentionLimit;
    }

    /** Terms whose retention runs on at the rate with no cap. */
    public PaymentTerms(int dueDays, int noticeDays, int payLessDays, int finalDays, Percent retention) {
        this(dueDays, noticeDays, payLessDays, finalDays, retention, Optional.empty());
    }

    /**
     * The dates an application sets running.
     *
     * @param dueOn the day payment falls due
     * @param noticeBy the last day to serve a payment notice
     * @param finalDateForPayment the last day to pay
     * @param payLessBy the last day to serve a pay less notice
     */
    public record Dates(LocalDate dueOn, LocalDate noticeBy, LocalDate finalDateForPayment, LocalDate payLessBy) {}

    /**
     * What is payable on one application.
     *
     * @param dates the dates it set running
     * @param basis what the sum was worked out from
     * @param notifiedGross the cumulative figure the notified sum is worked out from
     * @param retention the retention held on it, after any cap
     * @param netOfRetention the gross less retention
     * @param payable what is payable this time
     * @param byDefault true when the payer lost the argument by missing a notice
     */
    public record Position(
            Dates dates,
            NotifiedBasis basis,
            Money notifiedGross,
            Money retention,
            Money netOfRetention,
            Money payable,
            boolean byDefault) {

        /** {@code payable on the application, no notice served in time, final date 2025-06-08}. */
        public String describe() {
            String how = basis == NotifiedBasis.APPLICATION_BY_DEFAULT
                    ? "the application, no notice served in time"
                    : basis.wire().replace('_', ' ');
            return "payable on " + how + ", final date " + SiteDates.format(dates.finalDateForPayment());
        }
    }

    /** Check terms, refusing a window that is negative and a pay less window before the due date. */
    public static PaymentTerms build(PaymentTerms terms) {
        checkDays("dueDays", terms.dueDays);
        checkDays("noticeDays", terms.noticeDays);
        checkDays("payLessDays", terms.payLessDays);
        checkDays("finalDays", terms.finalDays);
        if (terms.retentionLimit.isPresent()
                && (terms.retentionLimit.get().basisPoints() < 0
                        || terms.retentionLimit.get().compareTo(Percent.FULL) > 0)) {
            throw new BadInputException(
                    "a retention limit runs from 0 to 100 percent of the order",
                    Details.of("retentionLimit", terms.retentionLimit.get().format()));
        }
        if (terms.finalDays < terms.payLessDays) {
            throw new BadInputException(
                    "the pay less window cannot start before the due date",
                    Details.of("finalDays", terms.finalDays, "payLessDays", terms.payLessDays));
        }
        return terms;
    }

    private static void checkDays(String name, int value) {
        if (value < 0) {
            throw new BadInputException(name + " is a whole number of days", Details.of("value", value));
        }
    }

    public PaymentTerms withDueDays(int days) {
        return new PaymentTerms(days, noticeDays, payLessDays, finalDays, retention, retentionLimit);
    }

    public PaymentTerms withNoticeDays(int days) {
        return new PaymentTerms(dueDays, days, payLessDays, finalDays, retention, retentionLimit);
    }

    public PaymentTerms withPayLessDays(int days) {
        return new PaymentTerms(dueDays, noticeDays, days, finalDays, retention, retentionLimit);
    }

    public PaymentTerms withFinalDays(int days) {
        return new PaymentTerms(dueDays, noticeDays, payLessDays, days, retention, retentionLimit);
    }

    public PaymentTerms withRetention(Percent other) {
        return new PaymentTerms(dueDays, noticeDays, payLessDays, finalDays, other, retentionLimit);
    }

    /** These terms with the retention capped at {@code other} of the order value, or uncapped for null. */
    public PaymentTerms withRetentionLimit(Percent other) {
        return new PaymentTerms(dueDays, noticeDays, payLessDays, finalDays, retention, Optional.ofNullable(other));
    }

    /** The dates {@code application} sets running under these terms. */
    public Dates datesFor(PaymentApplication application) {
        LocalDate dueOn = application.appliedOn().plusDays(dueDays);
        LocalDate finalDate = dueOn.plusDays(finalDays);
        return new Dates(dueOn, dueOn.plusDays(noticeDays), finalDate, finalDate.minusDays(payLessDays));
    }

    /**
     * What is actually payable on an application.
     *
     * <p>The order is the order the statute puts it in, and it is not the order people expect. A
     * pay less notice served in time beats a payment notice; a payment notice served in time beats
     * the application; and an application beats everything when neither notice arrived, however
     * wrong the figure is. That last rule is the one that costs money, so it is the one the report
     * names.
     */
    public Position position(
            PaymentApplication application, Optional<PaymentNotice> notice, Optional<PayLessNotice> payLess) {
        return position(application, notice, payLess, Optional.empty());
    }

    /**
     * What is actually payable on an application under an order of a known value.
     *
     * <p>Retention is held at the rate until the running total reaches the limit, and no further.
     * The limit is set against the order rather than against the application, so a subcontractor
     * applying for more than the order is worth does not have more held for it.
     *
     * @param orderValue the order the retention limit is set against, empty to leave it uncapped
     */
    public Position position(
            PaymentApplication application,
            Optional<PaymentNotice> notice,
            Optional<PayLessNotice> payLess,
            Optional<Money> orderValue) {
        Dates dates = datesFor(application);
        Currency currency = application.gross().currency();
        boolean noticeInTime =
                notice.map(n -> !n.servedOn().isAfter(dates.noticeBy())).orElse(false);
        boolean payLessInTime =
                payLess.map(n -> !n.servedOn().isAfter(dates.payLessBy())).orElse(false);
        Money notifiedGross = noticeInTime ? notice.get().valued() : application.gross();
        NotifiedBasis basis = payLessInTime
                ? NotifiedBasis.PAY_LESS_NOTICE
                : noticeInTime ? NotifiedBasis.PAYMENT_NOTICE : NotifiedBasis.APPLICATION_BY_DEFAULT;
        Money atRate = retention.of(notifiedGross);
        Money held = retentionLimit
                .flatMap(limit -> orderValue.map(limit::of))
                .map(cap -> Money.min(atRate, cap))
                .orElse(atRate);
        Money netOfRetention = notifiedGross.minus(held);
        Money fromNotices = netOfRetention.minus(application.previouslyPaid());
        Money payable = payLessInTime ? payLess.get().amount() : Money.max(fromNotices, Money.zero(currency));
        return new Position(
                dates,
                basis,
                notifiedGross,
                held,
                netOfRetention,
                payable,
                basis == NotifiedBasis.APPLICATION_BY_DEFAULT);
    }

    /** Applications the payer has not answered and still has time on. */
    public List<PaymentApplication> noticesDue(
            List<PaymentApplication> applications, List<PaymentNotice> served, LocalDate asAt) {
        Set<String> answered = answered(served);
        return applications.stream()
                .filter(a ->
                        !answered.contains(a.id()) && !datesFor(a).noticeBy().isBefore(asAt))
                .toList();
    }

    /** Applications where the notice window has already closed unanswered. */
    public List<PaymentApplication> defaultedApplications(
            List<PaymentApplication> applications, List<PaymentNotice> served, LocalDate asAt) {
        Set<String> answered = answered(served);
        return applications.stream()
                .filter(a ->
                        !answered.contains(a.id()) && datesFor(a).noticeBy().isBefore(asAt))
                .toList();
    }

    private static Set<String> answered(List<PaymentNotice> served) {
        Set<String> answered = new HashSet<>();
        served.forEach(n -> answered.add(n.applicationId()));
        return answered;
    }
}
