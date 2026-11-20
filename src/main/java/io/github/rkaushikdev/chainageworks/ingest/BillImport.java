package io.github.rkaushikdev.chainageworks.ingest;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads a priced bill export, and writes a bill back out in the same shape. */
public final class BillImport {

    /** The columns a bill export has to carry. */
    public static final List<String> BILL_COLUMNS = List.of("code", "description", "unit", "rate", "quantity");

    /** The columns {@link #exportBill} writes. */
    public static final List<String> BILL_EXPORT_COLUMNS =
            List.of("code", "description", "unit", "rate", "quantity", "kind", "from", "to");

    private static final List<String> KINDS =
            Arrays.stream(BillItemKind.values()).map(BillItemKind::wire).toList();

    private BillImport() {}

    /**
     * Who the imported bill is and what it is priced in. Any null takes the default: {@code B1},
     * {@code bill of quantities}, sterling.
     *
     * @param id the bill's key
     * @param title what the bill is called
     * @param currency what the rates are in
     */
    public record Options(String id, String title, Currency currency) {

        public static final Options DEFAULT = new Options(null, null, null);

        Currency currencyOrDefault() {
            return currency == null ? Currency.GBP : currency;
        }
    }

    /** {@link #importItems(String, Options)} in sterling. */
    public static ImportReport<BillItem> importItems(String text) {
        return importItems(text, Options.DEFAULT);
    }

    /**
     * Read a priced bill export.
     *
     * <p>The kind column decides whether the money is earnable, so a sheet that omits it gets
     * {@code measured} for everything except a sum, which gets {@code lump}. That default is the
     * safe one: it never turns provisional money into claimable money by accident, because a bill
     * that carries provisional items and does not say so is a bill that has already lost the
     * distinction.
     */
    public static ImportReport<BillItem> importItems(String text, Options options) {
        Csv.Table table = Csv.parse(text);
        Optional<ImportReport<BillItem>> columnProblem = ImportReport.checkColumns(table, BILL_COLUMNS);
        if (columnProblem.isPresent()) {
            return columnProblem.get();
        }
        Currency currency = options.currencyOrDefault();
        List<BillItem> imported = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Csv.Row row : table.rows()) {
            Csv.RowReader reader = table.readerFor(row);
            try {
                BillItem item = readItem(reader, currency);
                String key = item.code().format();
                if (!seen.add(key)) {
                    issues.add(ImportIssue.error("item " + key + " appears twice", row.line(), "code"));
                    continue;
                }
                imported.add(item);
            } catch (RuntimeException failure) {
                issues.add(ImportIssue.from(failure, reader.line()));
            }
        }
        return new ImportReport<>(imported, issues, table.rows().size());
    }

    private static BillItem readItem(Csv.RowReader reader, Currency currency) {
        Quantity quantity = Fields.needQuantity(reader, "quantity", "unit");
        BillItemKind kind = Fields.optional(reader, "kind").isEmpty()
                ? quantity.unit() == Unit.SUM ? BillItemKind.LUMP : BillItemKind.MEASURED
                : BillItemKind.fromWire(Fields.needOneOf(reader, "kind", KINDS)).orElseThrow();
        Optional<ChainageRange> chainage = Fields.optional(reader, "from").isEmpty()
                        || Fields.optional(reader, "to").isEmpty()
                ? Optional.empty()
                : Optional.of(new ChainageRange(Fields.needStation(reader, "from"), Fields.needStation(reader, "to")));
        return BillItem.build(new BillItemInput(
                Fields.needCode(reader, "code"),
                Fields.need(reader, "description"),
                Rate.fromMoney(Fields.needMoney(reader, "rate", currency), quantity.unit()),
                quantity,
                kind,
                chainage));
    }

    /** {@link #importBill(String, Options)} with the default options. */
    public static Bill importBill(String text) {
        return importBill(text, Options.DEFAULT);
    }

    /** Read a whole bill in one go, when the file is expected to be clean: the first error throws. */
    public static Bill importBill(String text, Options options) {
        ImportReport<BillItem> report = importItems(text, options);
        Optional<ImportIssue> first = report.errors().stream().findFirst();
        if (first.isPresent()) {
            ImportIssue problem = first.get();
            throw new BadInputException(problem.line()
                    .map(line -> "line " + line + ": " + problem.message())
                    .orElse(problem.message()));
        }
        return Bill.build(
                options.id() == null ? "B1" : options.id(),
                options.title() == null ? "bill of quantities" : options.title(),
                options.currencyOrDefault(),
                report.imported());
    }

    /** Write the bill back out in the shape the importer reads. */
    public static String exportBill(Bill bill) {
        return Csv.write(
                BILL_EXPORT_COLUMNS,
                bill.items().stream()
                        .map(item -> List.of(
                                item.code().format(),
                                item.description(),
                                item.unit().code(),
                                Numbers.toFixed(item.rate().major(), 4),
                                Numbers.format(item.quantity().amount()),
                                item.kind().wire(),
                                item.chainage()
                                        .map(r -> Stations.format(r.from()))
                                        .orElse(""),
                                item.chainage()
                                        .map(r -> Stations.format(r.to()))
                                        .orElse("")))
                        .toList());
    }
}
