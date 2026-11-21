package io.github.rkaushikdev.chainageworks.app;

import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.reporting.CsvExport;
import io.github.rkaushikdev.chainageworks.reporting.OutturnReport;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.RiskReport;
import io.github.rkaushikdev.chainageworks.reporting.format.Markdown;
import io.github.rkaushikdev.chainageworks.reporting.format.PlainText;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a month end produces for one valuation.
 *
 * @param valuation the valuation number
 * @param cutOff the valuation's cut-off, written {@code 2025-03-25}
 * @param diagnosis what the doctor found on the job before anything was built
 * @param files the reports, in the order they are built and written
 * @param amountDue the amount due this month in major units, {@code 15344.87}, which is the one figure
 *     everyone asks for
 */
public record MonthEndPack(int valuation, String cutOff, Diagnosis diagnosis, List<PackFile> files, String amountDue) {

    public MonthEndPack {
        Objects.requireNonNull(cutOff, "cutOff");
        Objects.requireNonNull(diagnosis, "diagnosis");
        files = List.copyOf(files);
        Objects.requireNonNull(amountDue, "amountDue");
    }

    /**
     * Everything a month end produces, in one call.
     *
     * <p>The order matters and it is the order a commercial team actually works in. The job is checked
     * first, because a report built on a dangling reference is worse than no report. The certificate
     * comes next because everything else is read against it. The forward looking reports come last,
     * because nobody plans next month before this month's number is agreed.
     *
     * <p>The check does not stop the pack. A job with a broken reference still has to produce a
     * valuation on the twenty fifth, and a tool that refuses would be put down and the valuation done in
     * a spreadsheet. The findings ride along so the reader knows what to distrust. The delay and defects
     * reports are left out when the contract fixes no completion date or no defects period.
     */
    public static MonthEndPack build(Service service, int valuation) {
        Diagnosis diagnosis = Doctor.diagnose(service.job());
        ValuationPeriod period = service.period(valuation);
        Certificate certificate = service.certificateFor(valuation);
        ProgressReport progress = service.progressFor(valuation);
        int worst = service.config().reports().worstLines();

        List<PackFile> files = new ArrayList<>();
        files.add(new PackFile("certificate.md", Markdown.certificate(certificate)));
        files.add(new PackFile("certificate.txt", PlainText.certificate(certificate)));
        files.add(new PackFile("certificate.csv", CsvExport.certificate(certificate)));
        files.add(new PackFile("certificate-totals.csv", CsvExport.certificateTotals(certificate)));
        files.add(new PackFile("progress.md", Markdown.progress(progress)));
        files.add(new PackFile("payments.md", Markdown.subcontract(service.paymentsFor(valuation))));
        files.add(
                new PackFile("variations.txt", service.variationsFor(valuation).render()));
        Outturn outturn = service.outturnFor(valuation);
        files.add(new PackFile("outturn.txt", OutturnReport.render(outturn, worst)));
        files.add(new PackFile("outturn.csv", CsvExport.outturn(outturn)));
        files.add(new PackFile("output.txt", service.outputFor(valuation).render()));
        files.add(new PackFile("risks.txt", RiskReport.render(service.risksFor(valuation))));
        service.delayFor(valuation).ifPresent(delay -> files.add(new PackFile("delay.txt", delay.render())));
        service.defectsFor(valuation).ifPresent(defects -> files.add(new PackFile("defects.txt", defects.render())));

        return new MonthEndPack(
                valuation,
                SiteDates.format(period.cutOff()),
                diagnosis,
                files,
                Numbers.format(certificate.amountDue().minor() / 100.0));
    }

    /** True when the pack was built on a job with nothing wrong with it. */
    public boolean isSound() {
        return diagnosis.isHealthy();
    }

    /** The file called {@code name}, if the pack carries one. */
    public Optional<PackFile> file(String name) {
        return files.stream().filter(file -> file.name().equals(name)).findFirst();
    }

    /** A one line summary for whoever is running the month end. */
    public String describe() {
        String health = isSound() ? "the job checks out" : diagnosis.errors() + " errors on the job";
        return "valuation " + valuation + " to " + cutOff + ", " + files.size() + " files, " + health;
    }

    /**
     * Write every file into {@code directory} as UTF-8, creating the directory and its parents when
     * they are not there, and answer the paths written, in pack order. A file already there under the
     * same name is replaced.
     */
    public List<Path> writeTo(Path directory) throws IOException {
        Files.createDirectories(directory);
        List<Path> written = new ArrayList<>(files.size());
        for (PackFile file : files) {
            Path path = directory.resolve(file.name());
            Files.writeString(path, file.text(), StandardCharsets.UTF_8);
            written.add(path);
        }
        return List.copyOf(written);
    }
}
