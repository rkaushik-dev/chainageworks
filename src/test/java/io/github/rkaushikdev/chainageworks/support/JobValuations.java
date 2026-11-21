package io.github.rkaushikdev.chainageworks.support;

import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.JobSettings;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.CertificateInput;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import io.github.rkaushikdev.chainageworks.valuation.OutturnInput;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.time.LocalDate;
import java.util.List;

/**
 * A job's certificate, progress report, payment position and outturn at a valuation number, read
 * from every register and term the job holds, for the report tests that start from a whole job.
 *
 * <p>The schedule is anchored on the month the first bill took effect and built out to at least
 * {@link #PERIODS} periods, and certificates are always run from period one, so the figures are the
 * ones the job itself would issue.
 */
public final class JobValuations {

    /** How many periods a schedule is built out to when nobody says. */
    public static final int PERIODS = 36;

    private JobValuations() {}

    /** The valuation schedule of {@code count} periods from the month the first bill took effect. */
    public static PeriodSchedule schedule(Job job, int count) {
        SiteDates.DateParts first =
                SiteDates.parts(job.context().billHistory().first().effectiveFrom());
        return job.schedule(first.year(), first.month(), count);
    }

    public static ValuationPeriod period(Job job, int number) {
        return schedule(job, Math.max(number, PERIODS)).byNumber(number);
    }

    /** The certificate input every period of the job's series is valued from. */
    public static CertificateInput certificateInput(Job job) {
        JobSettings settings = job.settings();
        return new CertificateInput(
                job.context(),
                settings.contractSum(),
                settings.retention(),
                job.materials().all(),
                settings.materialsAllowance().orElse(null),
                settings.daywork().orElse(null),
                job.dayworkSheets().all(),
                settings.adjustment().orElse(null),
                job.indices().all(),
                settings.retentionBond().orElse(null),
                settings.substantialCompletionOn().orElse(null),
                settings.defectsCertificateOn().orElse(null));
    }

    /** Every certificate from period one to period {@code count}. */
    public static List<Certificate> certificates(Job job, int count) {
        PeriodSchedule schedule = schedule(job, Math.max(count, PERIODS));
        return Certificate.series(schedule.periods().subList(0, count), certificateInput(job));
    }

    public static Certificate certificate(Job job, int number) {
        return certificates(job, number).get(number - 1);
    }

    public static ProgressReport progress(Job job, int number) {
        return ProgressReport.build(job.context(), period(job, number));
    }

    public static SubcontractReport payments(Job job, int number) {
        return SubcontractReport.build(
                job.subcontracts().all(),
                new Subcontract.Inputs(
                        job.paymentApplications().all(),
                        job.paymentNotices().all(),
                        job.payLessNotices().all(),
                        job.contraCharges().all(),
                        job.settings().contraNotifyDays()),
                period(job, number).cutOff());
    }

    /** The outturn input at a date, carrying every commercial term and register the job holds. */
    public static OutturnInput outturnInput(Job job, LocalDate asAt) {
        JobSettings settings = job.settings();
        ProgressContext context = job.context();
        return new OutturnInput(
                context,
                asAt,
                settings.contractSum(),
                job.variations().all(),
                settings.daywork().orElse(null),
                job.dayworkSheets().all(),
                settings.adjustment().orElse(null),
                job.indices().all(),
                settings.completion().orElse(null),
                job.delayEvents().all(),
                job.calendar(),
                null);
    }

    public static Outturn outturn(Job job, int number) {
        return Outturn.build(outturnInput(job, period(job, number).cutOff()));
    }
}
