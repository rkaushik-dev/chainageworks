package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PayLessNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.io.Csv;
import java.util.List;
import java.util.Optional;

/** Reads subcontract payment applications, payment notices and pay less notices. */
public final class PaymentImport {

    /** The columns an application schedule has to carry. */
    public static final List<String> APPLICATION_COLUMNS =
            List.of("application", "subcontract", "valuation", "applied on", "gross");

    /** The columns a payment notice schedule has to carry. */
    public static final List<String> NOTICE_COLUMNS = List.of("application", "served on", "valued");

    /** The columns a pay less notice schedule has to carry. */
    public static final List<String> PAY_LESS_COLUMNS = List.of("application", "served on", "amount", "reason");

    private PaymentImport() {}

    /**
     * Read a subcontract payment application schedule.
     *
     * <p>The previously paid column is optional and falls back to nothing, because the first
     * application against an order has no such figure and a commercial clerk exporting one row
     * should not have to type a zero to say so.
     */
    public static ImportReport<PaymentApplication> importApplications(String text, Currency currency) {
        return Rows.readTable(
                text,
                Rows.RowImport.of(APPLICATION_COLUMNS, (reader, issues) -> readApplication(reader, currency))
                        .keyedBy(PaymentApplication::id)
                        .duplicateSays(id -> "application " + id + " appears twice")
                        .keyColumn("application"));
    }

    private static PaymentApplication readApplication(Csv.RowReader reader, Currency currency) {
        Optional<String> previously = Fields.optional(reader, "previously paid");
        String id = Fields.need(reader, "application");
        String subcontractId = Fields.need(reader, "subcontract");
        int period = Fields.needWholeNumber(reader, "valuation");
        var appliedOn = Fields.needDate(reader, "applied on");
        Money gross = Fields.needMoney(reader, "gross", currency);
        Money paid =
                previously.isEmpty() ? Money.zero(currency) : Fields.needMoney(reader, "previously paid", currency);
        return new PaymentApplication(id, subcontractId, period, appliedOn, gross, paid);
    }

    /**
     * Read the payment notices the payer has served.
     *
     * <p>Keyed on the application rather than carrying an id of their own. A notice that answers no
     * application is not a notice, and giving these rows their own identity would let one be filed
     * against nothing at all.
     */
    public static ImportReport<PaymentNotice> importNotices(String text, Currency currency) {
        return Rows.readTable(
                text,
                Rows.RowImport.of(
                                NOTICE_COLUMNS,
                                (reader, issues) -> new PaymentNotice(
                                        Fields.need(reader, "application"),
                                        Fields.needDate(reader, "served on"),
                                        Fields.needMoney(reader, "valued", currency)))
                        .keyedBy(PaymentNotice::applicationId)
                        .duplicateSays(id -> "application " + id + " has two notices against it")
                        .keyColumn("application"));
    }

    /** Read the pay less notices, which carry the reason they have to give. */
    public static ImportReport<PayLessNotice> importPayLessNotices(String text, Currency currency) {
        return Rows.readTable(
                text,
                Rows.RowImport.of(
                                PAY_LESS_COLUMNS,
                                (reader, issues) -> new PayLessNotice(
                                        Fields.need(reader, "application"),
                                        Fields.needDate(reader, "served on"),
                                        Fields.needMoney(reader, "amount", currency),
                                        Fields.need(reader, "reason")))
                        .keyedBy(PayLessNotice::applicationId)
                        .duplicateSays(id -> "application " + id + " has two pay less notices against it")
                        .keyColumn("application"));
    }
}
