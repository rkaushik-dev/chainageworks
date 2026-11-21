package io.github.rkaushikdev.chainageworks.reporting;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.io.Csv;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.util.Text;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import java.time.LocalDate;
import java.util.List;

/**
 * The job's tables as comma separated text for a spreadsheet: the bill, the survey book, the
 * certificate and the final account.
 *
 * <p>Numbers go out unformatted, with no symbol and no thousands separator. A spreadsheet is the
 * only reader of these files and it wants a number it can add up. Sending {@code £16,152.50}
 * produces a column of text that silently sums to nothing, which is the single most common way a
 * CSV export wastes an afternoon.
 */
public final class CsvExport {

    private CsvExport() {}

    /** An amount in minor units as major units to two places: {@code 16152.50}. */
    private static String amount(long minor) {
        return Numbers.toFixed(minor / 100.0, 2);
    }

    /** The bill in force at a date, priced. */
    public static String bill(ProgressContext context, LocalDate asAt) {
        Bill bill = context.billHistory().billAsAt(asAt);
        return Csv.write(
                List.of("code", "description", "unit", "quantity", "rate", "kind", "value"),
                bill.items().stream()
                        .map(item -> List.of(
                                item.code().format(),
                                item.description(),
                                item.unit().code(),
                                Numbers.format(item.quantity().amount()),
                                Numbers.format(item.rate().major()),
                                item.kind().wire(),
                                amount((long) Numbers.round(
                                        item.quantity().amount() * item.rate().major() * 100))))
                        .toList());
    }

    /** The survey book as it stands at a date, superseded records left out. */
    public static String records(ProgressContext context, LocalDate asAt) {
        return Csv.write(
                List.of(
                        "record",
                        "package",
                        "code",
                        "step",
                        "from",
                        "to",
                        "quantity",
                        "unit",
                        "measured on",
                        "booked by",
                        "method"),
                SurveyRecord.effective(context.records(), asAt).stream()
                        .map(record -> {
                            List<String> ends = Text.split(record.extent().format(), " to ");
                            return List.of(
                                    record.id(),
                                    record.packageId(),
                                    record.code().format(),
                                    record.stepId(),
                                    ends.get(0),
                                    ends.size() > 1 ? ends.get(1) : "",
                                    Numbers.format(record.quantity().amount()),
                                    record.quantity().unit().code(),
                                    SiteDates.format(record.measuredOn()),
                                    record.bookedBy(),
                                    record.method().wire());
                        })
                        .toList());
    }

    /**
     * The certificate as a spreadsheet, one row per allocation.
     *
     * <p>Flattened rather than nested, because that is the shape a pivot table wants. The package
     * columns repeat down the file on purpose: a reader that groups them itself can, and one that
     * does not still gets a table that adds up.
     */
    public static String certificate(Certificate certificate) {
        return Csv.write(
                List.of(
                        "valuation",
                        "cut off",
                        "package",
                        "percent",
                        "code",
                        "allocated",
                        "measured",
                        "earned",
                        "value",
                        "capped"),
                certificate.earnings().stream()
                        .flatMap(earning -> earning.allocations().stream()
                                .map(allocation -> allocationRow(certificate, earning, allocation)))
                        .toList());
    }

    private static List<String> allocationRow(
            Certificate certificate, PackageEarning earning, PackageEarning.Allocation allocation) {
        return List.of(
                String.valueOf(certificate.period().number()),
                SiteDates.format(certificate.period().cutOff()),
                earning.packageId(),
                Numbers.format(earning.fraction().value()),
                allocation.code().format(),
                Numbers.format(allocation.allocated().amount()),
                Numbers.format(allocation.measured().amount()),
                Numbers.format(allocation.earnedQuantity().amount()),
                amount(allocation.earnedValue().minor()),
                allocation.cappedByMeasurement() ? "yes" : "no");
    }

    /** The final account, one row an item, for the spreadsheet nobody escapes. */
    public static String outturn(Outturn outturn) {
        return Csv.write(
                List.of("code", "description", "billed", "measured", "forecast", "movement", "percent", "basis"),
                outturn.account().lines().stream()
                        .map(line -> List.of(
                                line.code().format(),
                                line.description(),
                                amount(line.billed().minor()),
                                amount(line.measured().minor()),
                                amount(line.forecast().minor()),
                                amount(line.movement().minor()),
                                Numbers.format(line.percentComplete()),
                                line.extrapolated() ? "trend" : "bill"))
                        .toList());
    }

    /** The certificate's lines and their amounts, for anybody reading the money columns back. */
    public static String certificateTotals(Certificate certificate) {
        return Csv.write(
                List.of("item", "amount"),
                certificate.lines().stream()
                        .map(line -> List.of(line.name(), amount(line.amount().minor())))
                        .toList());
    }

    /** The forecast outturn in major units. */
    public static double outturnTotalMajor(Outturn outturn) {
        return outturn.total().major();
    }
}
