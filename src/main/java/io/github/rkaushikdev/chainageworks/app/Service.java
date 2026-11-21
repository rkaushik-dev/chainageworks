package io.github.rkaushikdev.chainageworks.app;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PayLessNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.ingest.BillImport;
import io.github.rkaushikdev.chainageworks.ingest.DiaryImport;
import io.github.rkaushikdev.chainageworks.ingest.ImportReport;
import io.github.rkaushikdev.chainageworks.ingest.IndexImport;
import io.github.rkaushikdev.chainageworks.ingest.InspectionImport;
import io.github.rkaushikdev.chainageworks.ingest.PaymentImport;
import io.github.rkaushikdev.chainageworks.ingest.RecordImport;
import io.github.rkaushikdev.chainageworks.ingest.ResourceImport;
import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.progress.LookaheadInput;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.Baseline;
import io.github.rkaushikdev.chainageworks.reporting.CurvePoint;
import io.github.rkaushikdev.chainageworks.reporting.DefectsReport;
import io.github.rkaushikdev.chainageworks.reporting.DelayReport;
import io.github.rkaushikdev.chainageworks.reporting.OutputReport;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.RevisionReport;
import io.github.rkaushikdev.chainageworks.reporting.SCurve;
import io.github.rkaushikdev.chainageworks.reporting.StripMap;
import io.github.rkaushikdev.chainageworks.reporting.SubcontractReport;
import io.github.rkaushikdev.chainageworks.reporting.VariationReport;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.JobSettings;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import io.github.rkaushikdev.chainageworks.valuation.CashFlow;
import io.github.rkaushikdev.chainageworks.valuation.CashFlowInput;
import io.github.rkaushikdev.chainageworks.valuation.CashPoint;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.CertificateInput;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import io.github.rkaushikdev.chainageworks.valuation.OutturnInput;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What the HTTP layer and the command line both call.
 *
 * <p>Neither of them is allowed to reach into the store directly. Everything that changes a job goes
 * through here, so there is exactly one place that decides whether an import with errors is applied
 * and exactly one place that knows the valuation series has to be run from period one.
 *
 * <p>Every refusal is one of the job's own exceptions, so both callers map it the same way: a {@link
 * BadInputException} for a valuation number or count the schedule cannot value or a job with no bill
 * yet, a {@link NotFoundException} for a package the job does not carry, and a {@link
 * ConflictException} for a register that does not reconcile under strict reconciliation or a bill
 * reference that is already used.
 */
public final class Service {

    /** How many periods a schedule is built out to when nobody says. */
    public static final int DEFAULT_PERIODS = 36;

    private static final String COUNTS_FROM_ONE = "a valuation number counts from one";

    private final Job job;
    private final Config config;

    /** A service over {@code job}, configured by {@code config}. */
    public Service(Job job, Config config) {
        this.job = Objects.requireNonNull(job, "job");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** The job every report reads and every import writes to. */
    public Job job() {
        return job;
    }

    public Config config() {
        return config;
    }

    /**
     * A valuation number read as a number off a command line, refused unless it is a whole number
     * from one.
     *
     * @throws BadInputException for a fraction, a value that is not a number, or one below one
     */
    public static int valuationNumber(double number) {
        if (!Numbers.isInteger(number) || number < 1) {
            throw new BadInputException(COUNTS_FROM_ONE, Details.of("number", number));
        }
        return (int) number;
    }

    /**
     * The read model the progress engine values from, assembled fresh from the registers.
     *
     * @throws BadInputException when no bill has been loaded
     */
    public ProgressContext context() {
        return job.context();
    }

    /** The schedule out to {@link #DEFAULT_PERIODS} periods. */
    public PeriodSchedule schedule() {
        return schedule(DEFAULT_PERIODS);
    }

    /**
     * The valuation schedule of {@code count} periods, anchored on the month the first bill took
     * effect.
     *
     * <p>Anchored there rather than on today because the schedule has to be the same every time it is
     * asked for. A schedule that starts from the current month renumbers every certificate the moment
     * the calendar rolls over.
     *
     * @throws BadInputException for a count below one, or a job with no bill yet
     */
    public PeriodSchedule schedule(int count) {
        SiteDates.DateParts first =
                SiteDates.parts(context().billHistory().first().effectiveFrom());
        return job.schedule(first.year(), first.month(), count);
    }

    /**
     * Valuation period {@code number}, numbered the same however far out the schedule is built.
     *
     * @throws BadInputException for a number below one
     */
    public ValuationPeriod period(int number) {
        if (number < 1) {
            throw new BadInputException(COUNTS_FROM_ONE, Details.of("number", number));
        }
        return schedule(Math.max(number, DEFAULT_PERIODS)).byNumber(number);
    }

    /**
     * The progress report for valuation {@code number}.
     *
     * @throws ConflictException when strict reconciliation is on and the packages do not reconcile
     *     against the bill
     */
    public ProgressReport progressFor(int number) {
        ProgressReport report = ProgressReport.build(context(), period(number));
        if (config.strictReconciliation() && !report.reconciliation().isEmpty()) {
            throw new ConflictException(
                    "the packages do not reconcile against the bill",
                    Details.of("problems", report.reconciliation().size()));
        }
        return report;
    }

    /**
     * Every certificate from period one to period {@code count}.
     *
     * <p>Always run from the start, never from the period asked for. Each certificate takes the
     * previous one's net figure off itself, so a series that began in the middle would pay the whole of
     * the works again from that month, and asking the caller for a previous figure instead is how two
     * callers come to disagree about what was certified last month.
     *
     * @throws BadInputException for a negative count
     */
    public List<Certificate> certificateSeries(int count) {
        if (count < 0) {
            throw new BadInputException(
                    "a certificate series cannot run to fewer than no periods", Details.of("count", count));
        }
        PeriodSchedule schedule = schedule(Math.max(count, DEFAULT_PERIODS));
        return Certificate.series(schedule.periods().subList(0, count), certificateInput());
    }

    /** The certificate input every period of the series is valued from, carrying every term the job holds. */
    private CertificateInput certificateInput() {
        JobSettings settings = job.settings();
        return new CertificateInput(
                context(),
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

    /**
     * The interim certificate for valuation {@code number}, run from period one so the previous figure
     * is right.
     *
     * @throws BadInputException for a number below one
     */
    public Certificate certificateFor(int number) {
        List<Certificate> series = certificateSeries(Math.max(number, 0));
        if (number < 1) {
            throw new BadInputException("the schedule does not reach that valuation", Details.of("number", number));
        }
        return series.get(number - 1);
    }

    /** {@link #cashFlowFor(int, int)} with every certificate paid on its due date. */
    public List<CashPoint> cashFlowFor(int count) {
        return cashFlowFor(count, 0);
    }

    /**
     * The cash forecast over {@code count} periods, run off the same certificates the job would issue.
     *
     * <p>Built from the real series rather than from the baseline curve, because the question this
     * answers is what will actually arrive, and the baseline is by definition what was hoped for before
     * anything was measured.
     *
     * @param latePeriods how many periods after falling due the payer pays
     * @throws BadInputException for a negative number of late periods
     */
    public List<CashPoint> cashFlowFor(int count, int latePeriods) {
        List<Certificate> series = certificateSeries(count);
        return CashFlow.build(new CashFlowInput(
                series.stream().map(Certificate::period).toList(),
                series.stream().map(Certificate::netValuation).toList(),
                job.settings().currency(),
                latePeriods));
    }

    /**
     * The extension of time position at the cut-off of valuation {@code number}.
     *
     * <p>Empty when the contract fixes no completion date, which is the honest answer: a job with no
     * completion date cannot be late. The valuation number is checked either way.
     */
    public Optional<DelayReport> delayFor(int number) {
        JobSettings settings = job.settings();
        LocalDate cutOff = period(number).cutOff();
        return settings.completion()
                .map(terms -> DelayReport.build(
                        terms, job.delayEvents().all(), job.calendar(), settings.contractSum(), cutOff));
    }

    /**
     * The defects position at the cut-off of valuation {@code number}, and what is stopping the defects
     * certificate. Empty when the contract has not fixed a defects period.
     */
    public Optional<DefectsReport> defectsFor(int number) {
        Optional<LocalDate> ends = job.settings().defectsPeriodEnds();
        if (ends.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DefectsReport.build(
                job.defects().all(), ends.get(), period(number).cutOff()));
    }

    /**
     * The subcontract payment position at the cut-off of valuation {@code number}.
     *
     * <p>Run at the same cut-off as the certificate on purpose. The two sides of the job move on
     * different clocks otherwise, and a main contract valuation that has been certified while three
     * subcontract notice windows quietly closed is how a job ends up paying twice for the same argument.
     */
    public SubcontractReport paymentsFor(int number) {
        return SubcontractReport.build(
                job.subcontracts().all(),
                new Subcontract.Inputs(
                        job.paymentApplications().all(),
                        job.paymentNotices().all(),
                        job.payLessNotices().all(),
                        job.contraCharges().all(),
                        job.settings().contraNotifyDays()),
                period(number).cutOff());
    }

    /**
     * What the job produced for the hours it booked, at the cut-off of valuation {@code number}.
     *
     * <p>Never empty. A job with no returns loaded has produced nothing for no hours, which is a true
     * and useful answer, and it is different from a contract that fixes no such term at all.
     */
    public OutputReport outputFor(int number) {
        return OutputReport.build(
                context(),
                job.packages().all(),
                job.resourceReturns().all(),
                job.settings().outputAllowances(),
                period(number).cutOff());
    }

    /**
     * The haul plan for the job as it stands.
     *
     * <p>Sources and destinations are split by kind here rather than in the store, because that split
     * is a reading of the register rather than a fact about it. A stockpile is a destination this month
     * and a source the next, and forcing the register to choose would mean rewriting it every time the
     * job turns round.
     */
    public HaulAllocation haulPlan() {
        List<HaulSite> sites = job.haulSites().all();
        return HaulAllocation.allocate(new HaulAllocation.Input(
                sites.stream()
                        .filter(site -> site.kind() == SiteKind.CUT || site.kind() == SiteKind.BORROW)
                        .toList(),
                sites.stream()
                        .filter(site -> site.kind() == SiteKind.FILL || site.kind() == SiteKind.TIP)
                        .toList(),
                job.settings().haulLinks(),
                job.settings().haulRate()));
    }

    /**
     * What the anticipated final account at valuation {@code number} is rolled up from, for a what-if
     * on one of its terms.
     *
     * <p>Every term the contract carries is handed over, not just the ones the job happens to be using.
     * A clause that is absent stays absent all the way through, so a fixed price job and a job whose
     * indices have not moved yet produce visibly different reports rather than the same zero.
     */
    public OutturnInput outturnInputFor(int number) {
        JobSettings settings = job.settings();
        ProgressContext context = context();
        return new OutturnInput(
                context,
                period(number).cutOff(),
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

    /** The anticipated final account at the cut-off of valuation {@code number}. */
    public Outturn outturnFor(int number) {
        return Outturn.build(outturnInputFor(number));
    }

    /**
     * The variation register at the cut-off of valuation {@code number}.
     *
     * <p>Valued against the bill revision in force at that cut-off, not the latest one. A variation that
     * was priced at last year's rates stays priced at last year's rates on last year's certificate,
     * however many revisions have landed since.
     */
    public VariationReport variationsFor(int number) {
        LocalDate asAt = period(number).cutOff();
        return VariationReport.build(
                context().billHistory().billAsAt(asAt), job.variations().all(), asAt);
    }

    /**
     * The early warning register at the cut-off of valuation {@code number}.
     *
     * <p>Run at the cut-off like everything else, so the risk a report carries is the risk that was on
     * the register when the valuation was worked out. A register read as at today would let a risk
     * closed last week quietly disappear out of a certificate issued the month before.
     */
    public EarlyWarning.Position risksFor(int number) {
        return EarlyWarning.position(
                job.earlyWarnings().all(),
                period(number).cutOff(),
                job.settings().currency(),
                job.settings().riskMeetingDays());
    }

    /**
     * Every revision of the bill against the one before it.
     *
     * <p>Not keyed on a valuation, because the whole history is the answer. A reader asking what
     * happened to the rates wants the revision that moved them, not the state of the bill on some
     * particular cut-off.
     */
    public RevisionReport revisionsFor() {
        return RevisionReport.build(context().billHistory());
    }

    /** The lookahead from {@code from} over the standard four weeks, with one gang to a step. */
    public Lookahead lookaheadFrom(LocalDate from) {
        return Lookahead.build(lookaheadInput(from));
    }

    /**
     * The lookahead from {@code from} over {@code weeks} weeks, with one gang to a step.
     *
     * @throws BadInputException for fewer than one week
     */
    public Lookahead lookaheadFrom(LocalDate from, int weeks) {
        return Lookahead.build(lookaheadInput(from).withWeeks(weeks));
    }

    /**
     * The lookahead from a date, which is deliberately not a valuation number.
     *
     * <p>A valuation is a backward looking thing measured to a cut-off, and a lookahead is a forward
     * looking thing that usually starts today. Tying it to the valuation schedule would make it
     * impossible to ask the only question anybody asks of it, which is what the next four weeks hold.
     *
     * @param weeks how many weeks the window runs
     * @param gangs gangs available for each step
     * @throws BadInputException for fewer than one week or one gang
     */
    public Lookahead lookaheadFrom(LocalDate from, int weeks, int gangs) {
        return Lookahead.build(lookaheadInput(from).withWeeks(weeks).withGangs(gangs));
    }

    private LookaheadInput lookaheadInput(LocalDate from) {
        return LookaheadInput.of(
                context(), job.packages().all(), job.calendar(), job.settings().outputAllowances(), from);
    }

    /**
     * Planned against earned over the first {@code count} periods, against the contract sum spread on
     * an S curve over those periods.
     *
     * @throws BadInputException for a count below one
     */
    public List<CurvePoint> curveFor(int count) {
        PeriodSchedule schedule = schedule(count);
        return SCurve.build(context(), schedule, Baseline.sCurve(job.settings().contractSum(), count));
    }

    /**
     * Planned against earned over the first {@code count} periods, against what the programme said
     * would be earned.
     *
     * @throws BadInputException for a count below one, or a baseline with no periods
     */
    public List<CurvePoint> curveFor(int count, Baseline baseline) {
        Objects.requireNonNull(baseline, "baseline");
        return SCurve.build(context(), schedule(count), baseline);
    }

    /**
     * Where each step of package {@code packageId} has got to at the cut-off of valuation {@code
     * number}.
     *
     * @throws NotFoundException when the job carries no such package
     */
    public StripMap stripFor(String packageId, int number) {
        WorkPackage pkg = job.packages().require(packageId);
        return StripMap.build(context(), pkg, period(number).cutOff());
    }

    /**
     * Hand the report back, applying what it imported only when it found no errors or when {@code
     * partial} asks for the clean rows anyway.
     */
    private static <T> ImportReport<T> applyIfClean(ImportReport<T> report, boolean partial, Consumer<List<T>> apply) {
        if (report.hasErrors() && !partial) {
            return report;
        }
        apply.accept(report.imported());
        return report;
    }

    /** {@link #loadRecords(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<SurveyRecord> loadRecords(String text) {
        return loadRecords(text, false);
    }

    /**
     * Load a survey book export into the records.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<SurveyRecord> loadRecords(String text, boolean partial) {
        return applyIfClean(RecordImport.importSurveyRecords(text), partial, job.records()::putAll);
    }

    /** {@link #loadSignOffs(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<SignOff> loadSignOffs(String text) {
        return loadSignOffs(text, false);
    }

    /**
     * Load an inspection register export into the sign offs.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<SignOff> loadSignOffs(String text, boolean partial) {
        return applyIfClean(InspectionImport.importSignOffs(text), partial, job.signOffs()::putAll);
    }

    /** {@link #loadNcrs(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<Ncr> loadNcrs(String text) {
        return loadNcrs(text, false);
    }

    /**
     * Load a non conformance register export.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<Ncr> loadNcrs(String text, boolean partial) {
        return applyIfClean(InspectionImport.importNcrs(text), partial, job.ncrs()::putAll);
    }

    /** {@link #loadDiary(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<Standdown> loadDiary(String text) {
        return loadDiary(text, false);
    }

    /**
     * Add the standdowns out of a site diary to the ones the job already has.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<Standdown> loadDiary(String text, boolean partial) {
        return applyIfClean(DiaryImport.importStanddowns(text), partial, items -> {
            List<Standdown> standdowns = new ArrayList<>(job.standdowns());
            standdowns.addAll(items);
            job.setStanddowns(standdowns);
        });
    }

    /** {@link #loadHolidays(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<LocalDate> loadHolidays(String text) {
        return loadHolidays(text, false);
    }

    /**
     * Add the contract holidays in a list to the ones the job already has, each day once.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<LocalDate> loadHolidays(String text, boolean partial) {
        return applyIfClean(DiaryImport.importHolidays(text), partial, items -> {
            Set<LocalDate> holidays = new LinkedHashSet<>(job.holidays());
            holidays.addAll(items);
            job.setHolidays(List.copyOf(holidays));
        });
    }

    /** {@link #loadApplications(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<PaymentApplication> loadApplications(String text) {
        return loadApplications(text, false);
    }

    /**
     * Load a subcontract payment application schedule.
     *
     * <p>The three payment registers load against the job currency rather than carrying one of their
     * own. A subcontract priced in a different currency from the main contract is a real thing, but it
     * is not a thing a csv column can settle, and reading one here would let a typo change what is owed.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<PaymentApplication> loadApplications(String text, boolean partial) {
        Currency currency = job.settings().currency();
        return applyIfClean(
                PaymentImport.importApplications(text, currency), partial, job.paymentApplications()::putAll);
    }

    /** {@link #loadNotices(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<PaymentNotice> loadNotices(String text) {
        return loadNotices(text, false);
    }

    /**
     * Load the payment notices served, in the job currency.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<PaymentNotice> loadNotices(String text, boolean partial) {
        Currency currency = job.settings().currency();
        return applyIfClean(PaymentImport.importNotices(text, currency), partial, job.paymentNotices()::putAll);
    }

    /** {@link #loadPayLessNotices(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<PayLessNotice> loadPayLessNotices(String text) {
        return loadPayLessNotices(text, false);
    }

    /**
     * Load the pay less notices served, in the job currency.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<PayLessNotice> loadPayLessNotices(String text, boolean partial) {
        Currency currency = job.settings().currency();
        return applyIfClean(PaymentImport.importPayLessNotices(text, currency), partial, job.payLessNotices()::putAll);
    }

    /** {@link #loadIndices(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<IndexSeries> loadIndices(String text) {
        return loadIndices(text, false);
    }

    /**
     * Load a published cost index bulletin.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<IndexSeries> loadIndices(String text, boolean partial) {
        return applyIfClean(IndexImport.importIndexSeries(text), partial, job.indices()::putAll);
    }

    /** {@link #loadResourceReturns(String, boolean)}, applying nothing when any row failed. */
    public ImportReport<ResourceReturn> loadResourceReturns(String text) {
        return loadResourceReturns(text, false);
    }

    /**
     * Load the daily labour and plant returns.
     *
     * @param partial apply the rows that parsed even when some did not
     */
    public ImportReport<ResourceReturn> loadResourceReturns(String text, boolean partial) {
        return applyIfClean(ResourceImport.importResourceReturns(text), partial, job.resourceReturns()::putAll);
    }

    /**
     * Load a priced bill sheet as a new revision of the bill.
     *
     * <p>A bill is loaded as a new revision, never over the top of the last one. That is the whole
     * reason the history exists, and an import that replaced a revision in place would silently reprice
     * every certificate before it.
     *
     * @throws ConflictException when the job already has a revision under that reference
     */
    public ImportReport<BillItem> loadBill(String text, LoadBillOptions options) {
        Currency currency = job.settings().currency();
        ImportReport<BillItem> report = BillImport.importItems(text, new BillImport.Options(null, null, currency));
        if (report.hasErrors() && !options.partial()) {
            return report;
        }
        Bill bill = Bill.build(
                options.reference(), "bill of quantities, " + options.reference(), currency, report.imported());
        job.billRevisions().add(new BillRevision(options.reference(), options.effectiveFrom(), bill, options.note()));
        return report;
    }
}
