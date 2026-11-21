package io.github.rkaushikdev.chainageworks.support;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.range;

import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectInput;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectSeverity;
import io.github.rkaushikdev.chainageworks.domain.delay.CompletionTerms;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayCause;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEvent;
import io.github.rkaushikdev.chainageworks.domain.delay.DelayEventInput;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulLink;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.AdjustmentTerms;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexReading;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.IndexSeries;
import io.github.rkaushikdev.chainageworks.domain.fluctuation.Weighting;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturnInput;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarning;
import io.github.rkaushikdev.chainageworks.domain.risk.EarlyWarningInput;
import io.github.rkaushikdev.chainageworks.domain.risk.RiskOwner;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraChargeInput;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraReason;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentTerms;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.subcontract.SubcontractInput;
import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationInput;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationItem;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationStatus;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.JobSettingsInput;
import java.util.List;

/** Jobs loaded to different depths, from the bare settings to the full commercial side and the haul. */
public final class JobFixtures {

    private JobFixtures() {}

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static Job newJob() {
        return Job.create(JobSettingsInput.of("A1-IMP", "A1 improvement, section 2", gbp(312_190)));
    }

    /** A job loaded with the sample bill, packages, plans and site records. */
    public static Job sampleJob() {
        Job job = newJob();
        job.alignments()
                .add(Alignment.build("A1", "main carriageway", Stations.parse("12+000"), Stations.parse("14+000")));
        job.billRevisions()
                .add(new BillRevision("Rev A", d("2025-01-01"), BillFixtures.sampleBill(), "contract bill at award"));
        job.packages().putAll(PackageFixtures.samplePackages());
        job.plans().putAll(List.of(ContextFixtures.EARTHWORKS_PLAN, ContextFixtures.PAVEMENT_PLAN));
        job.signOffs().putAll(ContextFixtures.sampleSignOffs());
        job.ncrs().putAll(ContextFixtures.sampleNcrs());
        job.records().putAll(ContextFixtures.sampleRecords());
        return job;
    }

    /** A job with the settings and nothing loaded, for the blocker checks. */
    public static Job emptyJob() {
        return newJob();
    }

    private static ResourceLine gang(int count, double hours) {
        return new ResourceLine("GANG-EARTH", "earthworks gang", count, hours, HourKind.WORKING);
    }

    /**
     * The sample job with the commercial side loaded as well.
     *
     * <p>Kept apart from {@link #sampleJob()} on purpose. Most of the engine tests want a job with no
     * completion date and no defects period, because that is what a job looks like before anybody
     * argues about anything, and loading those terms into every fixture would hide the empty paths the
     * reports have to handle.
     */
    public static Job commercialJob() {
        Job job = sampleJob();

        job.setSettings(job.settings()
                .withCompletion(CompletionTerms.build(d("2025-09-26"), gbp(2500), Percent.of(10), null))
                .withDefectsPeriodEnds(d("2025-06-30"))
                .withOutputAllowances(OutputAllowance.build(
                        List.of(new OutputAllowance("strip", 10), new OutputAllowance("excavate", 12)))));

        job.resourceReturns()
                .add(ResourceReturn.build(ResourceReturnInput.of("RR-01", "WP-01", d("2025-03-03"), "A Kerrigan")
                        .withStep("strip")
                        .withLabour(List.of(gang(6, 8)))
                        .withPlant(List.of(new ResourceLine("PL-D6", "dozer", 1, 8, HourKind.WORKING)))));
        job.resourceReturns()
                .add(ResourceReturn.build(ResourceReturnInput.of("RR-02", "WP-01", d("2025-03-04"), "A Kerrigan")
                        .withStep("strip")
                        .withLabour(List.of(gang(6, 8)))));
        job.resourceReturns()
                .add(ResourceReturn.build(ResourceReturnInput.of("RR-03", "WP-01", d("2025-03-10"), "A Kerrigan")
                        .withStep("excavate")
                        .withLabour(List.of(gang(4, 10)))
                        .withPlant(List.of(new ResourceLine("PL-360", "22t excavator", 1, 4, HourKind.STANDING)))));
        job.resourceReturns()
                .add(ResourceReturn.build(ResourceReturnInput.of("RR-04", "WP-01", d("2025-03-11"), "A Kerrigan")
                        .withLabour(List.of(gang(2, 8)))
                        .withNote("general site tidy, no step named")));

        job.delayEvents()
                .add(DelayEvent.build(DelayEventInput.of(
                                "DE-01",
                                "EW/2025/003",
                                DelayCause.UTILITIES,
                                "live water main outside its recorded position",
                                d("2025-03-03"),
                                d("2025-03-14"))
                        .withNotifiedOn(d("2025-03-05"))
                        .withAwardedDays(8)));
        job.delayEvents()
                .add(DelayEvent.build(DelayEventInput.of(
                                "DE-02",
                                "EW/2025/004",
                                DelayCause.DESIGN,
                                "revised drainage layout issued late",
                                d("2025-03-10"),
                                d("2025-03-21"))
                        .withNotifiedOn(null)));

        job.defects()
                .add(Defect.build(DefectInput.of(
                                "DF-01",
                                "DEF/2025/001",
                                "WP-03",
                                "surface course ravelling at the joint",
                                d("2025-03-03"),
                                d("2025-03-17"))
                        .withExtent(range("12+400", "12+600"))));
        job.defects()
                .add(Defect.build(DefectInput.of(
                                "DF-02",
                                "DEF/2025/002",
                                "WP-01",
                                "missing barrier at the retaining wall",
                                d("2025-03-20"),
                                d("2025-03-21"))
                        .withSeverity(DefectSeverity.SAFETY)));

        job.subcontracts()
                .add(Subcontract.build(SubcontractInput.of(
                                "SC-EARTH",
                                "bulk earthworks and capping",
                                "Dowsett Plant Hire",
                                gbp(250_000),
                                d("2024-11-04"))
                        .withPackages(List.of("WP-01", "WP-02"))
                        .withCodes(List.of(ItemCode.parse("E423.1")))));
        job.subcontracts()
                .add(Subcontract.build(SubcontractInput.of(
                                "SC-FENCE", "permanent fencing", "Marchmont Fencing", gbp(31_000), d("2025-01-13"))
                        .withPackages(List.of("WP-03"))
                        .withTerms(PaymentTerms.build(
                                PaymentTerms.DEFAULT.withDueDays(21).withRetention(Percent.of(3))))));

        job.paymentApplications()
                .add(new PaymentApplication("SA-01", "SC-EARTH", 1, d("2025-01-25"), gbp(40_000), gbp(0)));
        job.paymentApplications()
                .add(new PaymentApplication("SA-02", "SC-EARTH", 2, d("2025-02-25"), gbp(90_000), gbp(38_000)));
        job.paymentNotices().add(new PaymentNotice("SA-02", d("2025-03-28"), gbp(85_000)));

        job.earlyWarnings()
                .add(EarlyWarning.build(EarlyWarningInput.of(
                                "EW-01",
                                "unrecorded services under the roundabout",
                                RiskOwner.EMPLOYER,
                                d("2025-02-10"),
                                gbp(60_000))
                        .withReference("EW/2025/001")
                        .withExtent(range("12+400", "12+600"))
                        .withMeetingOn(d("2025-02-18"))
                        .withLikelihood(Percent.of(40))
                        .withDelayDays(12)));
        job.earlyWarnings()
                .add(EarlyWarning.build(EarlyWarningInput.of(
                                "EW-02",
                                "soft spots in the formation south of the bridge",
                                RiskOwner.CONTRACTOR,
                                d("2025-02-20"),
                                gbp(20_000))
                        .withReference("EW/2025/002")
                        .withLikelihood(Percent.of(75))
                        .withDelayDays(5)));

        job.variations()
                .add(Variation.build(VariationInput.of(
                                "VO-01",
                                "VO/2025/001",
                                "extra rock excavation at the cutting",
                                List.of(VariationItem.atBillRates(ItemCode.parse("E423.1"), Quantity.of(500, Unit.M3))))
                        .withStatus(VariationStatus.INSTRUCTED)
                        .withInstructedOn(d("2025-02-10"))
                        .withExtent(range("12+300", "12+500"))));
        job.variations()
                .add(Variation.build(VariationInput.of(
                                "VO-02",
                                "VO/2025/002",
                                "temporary access off the haul road",
                                List.of(VariationItem.priced(
                                        ItemCode.parse("E532.1"),
                                        Quantity.of(200, Unit.M3),
                                        Rate.of(9.4, Currency.GBP, Unit.M3),
                                        ValuationBasis.PRO_RATA)))
                        .withStatus(VariationStatus.AGREED)
                        .withInstructedOn(d("2025-02-20"))
                        .withAgreedOn(d("2025-03-06"))));
        job.variations()
                .add(Variation.build(VariationInput.of(
                                "VO-03",
                                "VO/2025/003",
                                "additional drainage at the low point",
                                List.of(VariationItem.atBillRates(ItemCode.parse("E423.1"), Quantity.of(80, Unit.M3))))
                        .withStatus(VariationStatus.PROPOSED)));

        job.contraCharges()
                .add(ContraCharge.build(ContraChargeInput.of(
                                "CC-01", "SC-EARTH", "crane time and banksman, week 9", gbp(1800), d("2025-02-28"))
                        .withReference("CC/2025/001")
                        .withReason(ContraReason.ATTENDANCE)));
        job.contraCharges()
                .add(ContraCharge.build(ContraChargeInput.of(
                                "CC-02",
                                "SC-EARTH",
                                "capping relaid at 12+500 outside tolerance",
                                gbp(4200),
                                d("2025-03-04"))
                        .withReference("CC/2025/002")
                        .withReason(ContraReason.RECTIFICATION)
                        .withNotifiedOn(d("2025-03-10"))
                        .withDisputed(true)));
        job.contraCharges()
                .add(ContraCharge.build(ContraChargeInput.of(
                                "CC-03", "SC-EARTH", "kerb line struck by a dumper", gbp(950), d("2025-03-01"))
                        .withReference("CC/2025/003")
                        .withReason(ContraReason.DAMAGE)));
        return job;
    }

    /**
     * The commercial job with a fluctuation clause and the bulletin behind it.
     *
     * <p>A separate fixture rather than a flag on the last one, because a contract either has a price
     * adjustment clause or it does not, and every figure on every certificate moves depending on
     * which. Tests that do not care about the clause should not be quietly running against an
     * adjusted valuation.
     */
    public static Job fluctuatingJob() {
        Job job = commercialJob();

        job.indices()
                .add(IndexSeries.build(
                        "IDX-LAB",
                        "civil engineering labour",
                        List.of(
                                new IndexReading(2024, 10, 100, false),
                                new IndexReading(2024, 11, 101, false),
                                new IndexReading(2024, 12, 102, false),
                                new IndexReading(2025, 1, 105, false),
                                new IndexReading(2025, 2, 110, false),
                                new IndexReading(2025, 3, 120, true))));
        job.indices()
                .add(IndexSeries.build(
                        "IDX-PLA",
                        "plant hire",
                        List.of(
                                new IndexReading(2024, 10, 200, false),
                                new IndexReading(2025, 1, 210, false),
                                new IndexReading(2025, 3, 205, false))));

        job.setSettings(job.settings()
                .withAdjustment(AdjustmentTerms.build(
                        d("2024-10-25"),
                        Percent.of(10),
                        List.of(new Weighting("IDX-LAB", Percent.of(60)), new Weighting("IDX-PLA", Percent.of(30))))));
        return job;
    }

    /**
     * The commercial job with the earthworks haul set up as well.
     *
     * <p>Kept apart again, because a haul plan needs sites and distances that most of the engine has
     * no opinion about, and loading them into every fixture would make every unrelated snapshot longer
     * without making it truer.
     */
    public static Job haulingJob() {
        Job job = commercialJob();

        job.setSettings(job.settings()
                .withHaulRate(gbp(1))
                .withHaulLinks(List.of(
                        new HaulLink("CUT-A", "EMB-B", 3),
                        new HaulLink("CUT-A", "TIP-1", 1),
                        new HaulLink("BOR-1", "EMB-B", 2))));

        job.haulSites()
                .add(new HaulSite("CUT-A", "cutting at 12+400", SiteKind.CUT, Quantity.of(6000, Unit.M3), gbp(0)));
        job.haulSites()
                .add(new HaulSite(
                        "BOR-1", "borrow pit off the haul road", SiteKind.BORROW, Quantity.of(4000, Unit.M3), gbp(3)));
        job.haulSites()
                .add(new HaulSite("EMB-B", "embankment at 13+200", SiteKind.FILL, Quantity.of(7000, Unit.M3), gbp(0)));
        job.haulSites().add(new HaulSite("TIP-1", "licensed tip", SiteKind.TIP, Quantity.of(5000, Unit.M3), gbp(9)));
        return job;
    }
}
