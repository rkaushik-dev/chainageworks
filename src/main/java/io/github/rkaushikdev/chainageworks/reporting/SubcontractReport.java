package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.format.Column;
import io.github.rkaushikdev.chainageworks.reporting.format.Table;
import io.github.rkaushikdev.chainageworks.util.Collation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The subcontract payment position across the whole job.
 *
 * <p>The diary is the point of this report rather than the totals. A commercial manager can work
 * out what has been certified from any ledger, but nothing else on the job says which notice window
 * shuts on Friday, and that is the one fact that costs real money when it is missed.
 *
 * @param asAt the date the position is taken at
 * @param positions where each order stands, in the order the orders were given
 * @param diary every unanswered application, soonest notice date first
 * @param overOrder the orders certified past their value
 * @param exposure what the missed notices have cost, as an amount over the payer's own valuation
 * @param barredContra contra charges that lost their right to be set off, across every order
 */
public record SubcontractReport(
        LocalDate asAt,
        List<Subcontract.Position> positions,
        List<NoticeDiaryEntry> diary,
        List<Subcontract.Position> overOrder,
        String exposure,
        List<BarredContra> barredContra) {

    public SubcontractReport {
        positions = List.copyOf(positions);
        diary = List.copyOf(diary);
        overOrder = List.copyOf(overOrder);
        barredContra = List.copyOf(barredContra);
    }

    /**
     * One line of the notice diary: an application and the day the window shuts.
     *
     * @param applicationId the application
     * @param subcontractId the order it was made under
     * @param contractor who made it
     * @param appliedOn the day it was made
     * @param noticeBy the last day a payment notice can answer it
     * @param amount what was applied for, written out
     * @param missed true once the window has closed with nothing served
     */
    public record NoticeDiaryEntry(
            String applicationId,
            String subcontractId,
            String contractor,
            LocalDate appliedOn,
            LocalDate noticeBy,
            String amount,
            boolean missed) {}

    /**
     * The contra charges on one order that can no longer be set off.
     *
     * @param subcontractId the order
     * @param amount what they come to, written out
     */
    public record BarredContra(String subcontractId, String amount) {}

    /**
     * The position of every order in {@code subcontracts} at {@code asAt}, read from the
     * applications, notices and contra charges in {@code inputs}.
     */
    public static SubcontractReport build(List<Subcontract> subcontracts, Subcontract.Inputs inputs, LocalDate asAt) {
        List<Subcontract.Position> positions = subcontracts.stream()
                .map(subcontract -> subcontract.position(inputs, asAt))
                .toList();

        List<NoticeDiaryEntry> diary = new ArrayList<>();
        Currency currency = subcontracts.isEmpty()
                ? Currency.GBP
                : subcontracts.get(0).orderValue().currency();
        long exposureMinor = 0;

        for (Subcontract subcontract : subcontracts) {
            List<PaymentApplication> mine = inputs.applications().stream()
                    .filter(a -> a.subcontractId().equals(subcontract.id()))
                    .toList();
            List<PaymentApplication> open = subcontract.terms().noticesDue(mine, inputs.notices(), asAt);
            List<PaymentApplication> missed = subcontract.terms().defaultedApplications(mine, inputs.notices(), asAt);
            for (PaymentApplication application :
                    Stream.concat(open.stream(), missed.stream()).toList()) {
                boolean isMissed = missed.contains(application);
                if (isMissed) {
                    exposureMinor += application.gross().minor()
                            - application.previouslyPaid().minor();
                }
                diary.add(new NoticeDiaryEntry(
                        application.id(),
                        subcontract.id(),
                        subcontract.contractor(),
                        application.appliedOn(),
                        subcontract.terms().datesFor(application).noticeBy(),
                        application.gross().format(),
                        isMissed));
            }
        }

        diary.sort(Comparator.comparing(NoticeDiaryEntry::noticeBy)
                .thenComparing(NoticeDiaryEntry::applicationId, Collation.ORDER));

        return new SubcontractReport(
                asAt,
                positions,
                diary,
                Subcontract.overOrder(positions),
                Money.of(exposureMinor, currency).format(),
                positions.stream()
                        .filter(position -> position.contra().barred().minor() > 0)
                        .map(position -> new BarredContra(
                                position.subcontract().id(),
                                position.contra().barred().format()))
                        .toList());
    }

    /** The report as plain text: the counts, the orders and the notice diary. */
    public String render() {
        List<String> sections = new ArrayList<>();
        sections.add(Table.heading("subcontract payments at " + SiteDates.format(asAt)));
        sections.add("");
        sections.add(Table.pairs(List.of(
                Map.entry("orders", String.valueOf(positions.size())),
                Map.entry(
                        "notices outstanding",
                        String.valueOf(diary.stream().filter(e -> !e.missed()).count())),
                Map.entry(
                        "notices missed",
                        String.valueOf(
                                diary.stream().filter(NoticeDiaryEntry::missed).count())),
                Map.entry("over the order", String.valueOf(overOrder.size())),
                Map.entry("set off barred", String.valueOf(barredContra.size())),
                Map.entry("exposed by default", exposure))));

        if (!positions.isEmpty()) {
            sections.add("");
            sections.add(Table.render(
                    List.of(
                            Column.of("order"),
                            Column.of("contractor").withWidth(24),
                            Column.right("value"),
                            Column.right("certified"),
                            Column.right("paid"),
                            Column.right("set off"),
                            Column.right("net"),
                            Column.right("left")),
                    positions.stream()
                            .map(position -> List.of(
                                    position.subcontract().id(),
                                    position.subcontract().contractor(),
                                    position.subcontract().orderValue().format(),
                                    position.certifiedToDate().format(),
                                    position.paidToDate().format(),
                                    position.contra().deductible().format(),
                                    position.netOfContra().format(),
                                    position.remaining().format()))
                            .toList()));
        }

        if (!diary.isEmpty()) {
            sections.add("");
            sections.add(Table.heading("notice diary", "-"));
            sections.add(Table.render(
                    List.of(
                            Column.of("application"),
                            Column.of("contractor").withWidth(24),
                            Column.of("applied"),
                            Column.of("notice by"),
                            Column.right("applied for"),
                            Column.of("state")),
                    diary.stream()
                            .map(entry -> List.of(
                                    entry.applicationId(),
                                    entry.contractor(),
                                    SiteDates.format(entry.appliedOn()),
                                    SiteDates.format(entry.noticeBy()),
                                    entry.amount(),
                                    entry.missed() ? "window closed" : "open"))
                            .toList()));
        }
        return String.join("\n", sections);
    }

    /** The applications that have to be answered within {@code within} days of the report date. */
    public List<String> noticesThisWeek(int within) {
        return diary.stream()
                .filter(entry -> !entry.missed() && SiteDates.diffDays(asAt, entry.noticeBy()) <= within)
                .map(entry -> entry.applicationId() + " " + entry.contractor() + ", notice by "
                        + SiteDates.format(entry.noticeBy()))
                .toList();
    }
}
