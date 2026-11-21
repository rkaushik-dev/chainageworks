package io.github.rkaushikdev.chainageworks.store;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.bill.BillHistory;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditProfile;
import io.github.rkaushikdev.chainageworks.domain.credit.ProfileRegistry;
import io.github.rkaushikdev.chainageworks.domain.daywork.DayworkSheet;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PayLessNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.progress.ProgressContextInput;
import io.github.rkaushikdev.chainageworks.valuation.MaterialsOnSite;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One contract's worth of state.
 *
 * <p>The repositories hold what happened and the settings hold what the contract says. Keeping them
 * apart matters because the settings are the only part anybody edits by hand, and a bad edit there
 * quietly changes every valuation ever produced rather than one record.
 */
public final class Job {

    private JobSettings settings;
    private final Repository<Alignment> alignments = Repository.inMemory("alignment", Alignment::id);
    private final Repository<BillRevision> billRevisions =
            Repository.inMemory("bill revision", BillRevision::reference);
    private final Repository<WorkPackage> packages = Repository.inMemory("work package", WorkPackage::id);
    private final Repository<CreditProfile> profiles =
            Repository.inMemory("rule of credit", CreditProfile::id, CreditCatalogue.STANDARD_PROFILES);
    private final Repository<InspectionPlan> plans = Repository.inMemory("inspection plan", InspectionPlan::id);
    private final Repository<SignOff> signOffs = Repository.inMemory("sign off", SignOff::id);
    private final Repository<Ncr> ncrs = Repository.inMemory("non conformance", Ncr::id);
    private final Repository<SurveyRecord> records = Repository.inMemory("survey record", SurveyRecord::id);
    private final Repository<MaterialsOnSite> materials = Repository.inMemory("materials on site", MaterialsOnSite::id);
    private final Repository<Variation> variations = Repository.inMemory("variation", Variation::id);
    private final Repository<DayworkSheet> dayworkSheets = Repository.inMemory("daywork sheet", DayworkSheet::id);
    private final Repository<DelayEvent> delayEvents = Repository.inMemory("delay event", DelayEvent::id);
    private final Repository<Defect> defects = Repository.inMemory("defect", Defect::id);
    private final Repository<Subcontract> subcontracts = Repository.inMemory("subcontract", Subcontract::id);
    private final Repository<PaymentApplication> paymentApplications =
            Repository.inMemory("payment application", PaymentApplication::id);
    private final Repository<PaymentNotice> paymentNotices =
            Repository.inMemory("payment notice", PaymentNotice::applicationId);
    private final Repository<PayLessNotice> payLessNotices =
            Repository.inMemory("pay less notice", PayLessNotice::applicationId);
    private final Repository<ResourceReturn> resourceReturns =
            Repository.inMemory("resource return", ResourceReturn::id);
    private final Repository<IndexSeries> indices = Repository.inMemory("index series", IndexSeries::id);
    private final Repository<ContraCharge> contraCharges = Repository.inMemory("contra charge", ContraCharge::id);
    private final Repository<HaulSite> haulSites = Repository.inMemory("haul site", HaulSite::id);
    private final Repository<EarlyWarning> earlyWarnings = Repository.inMemory("early warning", EarlyWarning::id);
    private List<LocalDate> holidays = List.of();
    private List<Standdown> standdowns = List.of();

    private Job(JobSettings settings) {
        this.settings = settings;
    }

    /** A new job with the given settings, the standard rules of credit and nothing else. */
    public static Job create(JobSettingsInput input) {
        return new Job(JobSettings.build(input));
    }

    public JobSettings settings() {
        return settings;
    }

    /** Replace the settings, as a hand edit of the contract terms does. */
    public void setSettings(JobSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Repository<Alignment> alignments() {
        return alignments;
    }

    public Repository<BillRevision> billRevisions() {
        return billRevisions;
    }

    public Repository<WorkPackage> packages() {
        return packages;
    }

    public Repository<CreditProfile> profiles() {
        return profiles;
    }

    public Repository<InspectionPlan> plans() {
        return plans;
    }

    public Repository<SignOff> signOffs() {
        return signOffs;
    }

    public Repository<Ncr> ncrs() {
        return ncrs;
    }

    public Repository<SurveyRecord> records() {
        return records;
    }

    public Repository<MaterialsOnSite> materials() {
        return materials;
    }

    public Repository<Variation> variations() {
        return variations;
    }

    public Repository<DayworkSheet> dayworkSheets() {
        return dayworkSheets;
    }

    public Repository<DelayEvent> delayEvents() {
        return delayEvents;
    }

    public Repository<Defect> defects() {
        return defects;
    }

    public Repository<Subcontract> subcontracts() {
        return subcontracts;
    }

    public Repository<PaymentApplication> paymentApplications() {
        return paymentApplications;
    }

    public Repository<PaymentNotice> paymentNotices() {
        return paymentNotices;
    }

    public Repository<PayLessNotice> payLessNotices() {
        return payLessNotices;
    }

    public Repository<ResourceReturn> resourceReturns() {
        return resourceReturns;
    }

    public Repository<IndexSeries> indices() {
        return indices;
    }

    public Repository<ContraCharge> contraCharges() {
        return contraCharges;
    }

    public Repository<HaulSite> haulSites() {
        return haulSites;
    }

    public Repository<EarlyWarning> earlyWarnings() {
        return earlyWarnings;
    }

    /** The days the contract does not work, as the calendar is built from them. */
    public List<LocalDate> holidays() {
        return holidays;
    }

    public void setHolidays(List<LocalDate> holidays) {
        this.holidays = List.copyOf(holidays);
    }

    /** The working days the site diary says were lost. */
    public List<Standdown> standdowns() {
        return standdowns;
    }

    public void setStanddowns(List<Standdown> standdowns) {
        this.standdowns = List.copyOf(standdowns);
    }

    /** The site calendar the settings and the holiday list describe. */
    public SiteCalendar calendar() {
        return SiteCalendar.build(CalendarInput.of(settings.id() + "-calendar", settings.name() + " calendar")
                .withWorkingDays(settings.workingDays())
                .withHolidays(holidays)
                .withStanddowns(standdowns));
    }

    /**
     * Assemble the read model the progress engine wants.
     *
     * <p>Built fresh every time rather than cached. The engine reads eleven registers and any one of
     * them can have moved since the last call, and a stale context is a valuation that is quietly a
     * week out of date.
     *
     * @throws BadInputException when no bill has been loaded
     */
    public ProgressContext context() {
        if (billRevisions.count() == 0) {
            throw new BadInputException("the job has no bill yet", Details.of("job", settings.id()));
        }
        return ProgressContext.build(ProgressContextInput.of(
                        BillHistory.build(billRevisions.all()),
                        PackageRegister.build(packages.all()),
                        ProfileRegistry.build(profiles.all()),
                        calendar())
                .withPlans(plans.all())
                .withSignOffs(signOffs.all())
                .withNcrs(ncrs.all())
                .withRecords(records.all())
                .withTolerances(settings.tolerances()));
    }

    /** The valuation periods from a first month, on the job's cut-off and lags. */
    public PeriodSchedule schedule(int firstYear, int firstMonth, int count) {
        return PeriodSchedule.build(new ScheduleInput(
                firstYear,
                firstMonth,
                count,
                settings.cutOffDay(),
                settings.issueDays(),
                settings.paymentDays(),
                calendar()));
    }

    /** Whether the job has a bill and packages to value. */
    public boolean isReady() {
        return billRevisions.count() > 0 && packages.count() > 0;
    }

    /** What the job is missing before it can value anything. */
    public List<String> blockers() {
        List<String> blockers = new ArrayList<>();
        if (billRevisions.count() == 0) {
            blockers.add("no bill has been loaded");
        }
        if (packages.count() == 0) {
            blockers.add("no work packages have been set up");
        }
        if (alignments.count() == 0) {
            blockers.add("no alignment has been loaded");
        }
        return List.copyOf(blockers);
    }
}
