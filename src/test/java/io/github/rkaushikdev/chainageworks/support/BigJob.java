package io.github.rkaushikdev.chainageworks.support;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPoint;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrInput;
import io.github.rkaushikdev.chainageworks.domain.itp.PointType;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffInput;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackageInput;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.resource.HourKind;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceLine;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturn;
import io.github.rkaushikdev.chainageworks.domain.resource.ResourceReturnInput;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraCharge;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraChargeInput;
import io.github.rkaushikdev.chainageworks.domain.subcontract.ContraReason;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.Subcontract;
import io.github.rkaushikdev.chainageworks.domain.subcontract.SubcontractInput;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecordInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.OutputAllowance;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.JobSettingsInput;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A job the size of a real contract, built from a seed.
 *
 * <p>Two hundred packages and eight thousand records is a two year highway job, and it is the size at
 * which an accidentally quadratic lookup in the engine stops being invisible. The generator is seeded
 * so a timing regression can be reproduced rather than argued about.
 */
public final class BigJob {

    private static final List<String> RECORD_STEPS = List.of("strip", "excavate", "place", "trim");

    private static final List<String> RETURN_STEPS = List.of("strip", "excavate", "place");

    private static final List<ContraReason> CHARGE_REASONS =
            List.of(ContraReason.ATTENDANCE, ContraReason.WELFARE, ContraReason.RECTIFICATION, ContraReason.DAMAGE);

    private static final List<HourKind> GANG_HOURS =
            List.of(HourKind.WORKING, HourKind.WORKING, HourKind.STANDING, HourKind.IDLE);

    private BigJob() {}

    /**
     * How big to build it.
     *
     * @param packages packages down the alignment, each a kilometre long
     * @param recordsPerPackage survey records booked per package
     * @param subcontracts subcontract orders across the job, each with monthly applications
     * @param resourceReturns daily resource returns booked against the packages
     * @param seed what the generator starts from
     */
    public record Options(int packages, int recordsPerPackage, int subcontracts, int resourceReturns, long seed) {

        /** Two hundred packages of forty records, thirty orders and two thousand returns. */
        public static final Options DEFAULT = new Options(200, 40, 30, 2000, 20_250_301);
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    private static String padded(int number, int width) {
        return Text.padStart(Integer.toString(number), width, '0');
    }

    /** The job at {@link Options#DEFAULT}. */
    public static Job bigJob() {
        return bigJob(Options.DEFAULT);
    }

    /** A job of the size {@code options} asks for. */
    public static Job bigJob(Options options) {
        int packageCount = options.packages();
        int perPackage = options.recordsPerPackage();
        SeededRandom random = new SeededRandom(options.seed());

        Job job = Job.create(JobSettingsInput.of("BIG", "a job the size of a real one", gbp(50_000_000)));

        job.alignments()
                .add(Alignment.build("A1", "main carriageway", Stations.of(0), Stations.of(packageCount * 1_000_000L)));

        List<ItemCode> codes = List.of(
                ItemCode.of("E", List.of(4, 2, 3), "1"),
                ItemCode.of("E", List.of(5, 3, 2), "1"),
                ItemCode.of("E", List.of(4, 2, 4), "1"));

        List<BillItem> items = new ArrayList<>();
        for (int index = 0; index < codes.size(); index++) {
            items.add(BillItem.build(BillItemInput.of(
                    codes.get(index),
                    "bulk earthworks item " + (index + 1),
                    Rate.of(4 + index, Currency.GBP, Unit.M3),
                    Quantity.of(packageCount * 5000.0, Unit.M3))));
        }
        Bill bill = Bill.build("Rev A", "bill of quantities", Currency.GBP, items);
        job.billRevisions().add(new BillRevision("Rev A", d("2025-01-01"), bill, "contract bill at award"));

        job.plans()
                .add(InspectionPlan.build(
                        "ITP-EW",
                        "earthworks",
                        "bulk-earthworks",
                        List.of(
                                InspectionPoint.of("IP-E1", "formation level", PointType.HOLD, "excavate"),
                                InspectionPoint.of("IP-E2", "layer density", PointType.HOLD, "place")),
                        CreditCatalogue.BULK_EARTHWORKS));

        List<WorkPackage> packages = new ArrayList<>();
        List<SurveyRecord> records = new ArrayList<>();
        List<SignOff> signOffs = new ArrayList<>();
        List<Ncr> ncrs = new ArrayList<>();

        for (int i = 0; i < packageCount; i += 1) {
            long from = Stations.of(i * 1_000_000L);
            long to = Stations.of((i + 1) * 1_000_000L);
            String id = "WP-" + padded(i + 1, 4);
            packages.add(WorkPackage.build(new WorkPackageInput(
                    id,
                    "earthworks package " + id,
                    "A1",
                    ChainageRange.of(from, to),
                    "section " + (i / 20 + 1),
                    "bulk-earthworks",
                    codes.stream()
                            .map(code -> new PackageAllocation(code, Quantity.of(5000, Unit.M3)))
                            .toList())));

            for (int r = 0; r < perPackage; r += 1) {
                String step = random.pick(RECORD_STEPS);
                long start = random.between(0, 900) * 1000L;
                ItemCode code = random.pick(codes);
                long end = from + start + random.between(20, 100) * 1000L;
                Quantity quantity = Quantity.of(random.between(10, 400), Unit.M3);
                LocalDate measuredOn = SiteDates.addDays(d("2025-01-05"), random.between(0, 500));
                records.add(SurveyRecord.build(SurveyRecordInput.of(
                        id + "-SR-" + padded(r, 3),
                        id,
                        code,
                        step,
                        ChainageRange.of(Stations.of(from + start), Stations.of(end)),
                        quantity,
                        measuredOn,
                        "J Foreman")));
            }

            long signedTo = from + random.between(200, 1000) * 1000L;
            LocalDate signedOn = SiteDates.addDays(d("2025-02-01"), random.between(0, 400));
            signOffs.add(SignOff.build(SignOffInput.of(
                    id + "-SO-1", "IP-E1", id, ChainageRange.of(from, Stations.of(signedTo)), signedOn, "R Engineer")));

            if (random.bool(0.15)) {
                long start = random.between(0, 800) * 1000L;
                LocalDate raisedOn = SiteDates.addDays(d("2025-02-01"), random.between(0, 400));
                ncrs.add(Ncr.build(NcrInput.of(
                        id + "-NCR-1",
                        id,
                        ChainageRange.of(Stations.of(from + start), Stations.of(from + start + 100_000)),
                        raisedOn,
                        "layer thickness over the specification")));
            }
        }

        job.packages().putAll(packages);
        job.records().putAll(records);
        job.signOffs().putAll(signOffs);
        job.ncrs().putAll(ncrs);

        loadCommercial(job, packageCount, options, random);
        return job;
    }

    /**
     * The commercial side of a job this size.
     *
     * <p>Kept in its own pass rather than woven into the package loop, because the two are sized by
     * different things. A road job has two hundred packages and thirty subcontracts, and building the
     * orders inside the package loop would tie one number to the other and hide whichever is actually
     * slow.
     */
    private static void loadCommercial(Job job, int packageCount, Options options, SeededRandom random) {
        int orders = options.subcontracts();
        int returns = options.resourceReturns();

        job.setSettings(job.settings()
                .withHaulRate(gbp(1))
                .withOutputAllowances(OutputAllowance.build(
                        List.of(new OutputAllowance("strip", 10), new OutputAllowance("excavate", 12)))));

        for (int i = 0; i < orders; i += 1) {
            String id = "SC-" + padded(i + 1, 3);
            Money orderValue = gbp(random.between(100_000, 900_000));
            String packageId = "WP-" + padded(random.between(1, packageCount), 4);
            job.subcontracts()
                    .add(Subcontract.build(SubcontractInput.of(
                                    id,
                                    "earthworks package " + id,
                                    "contractor " + (i + 1),
                                    orderValue,
                                    d("2024-12-02"))
                            .withPackages(List.of(packageId))));

            long paid = 0;
            for (int period = 1; period <= 12; period += 1) {
                long gross = paid + random.between(5000, 40_000);
                String applicationId = id + "-SA-" + padded(period, 2);
                job.paymentApplications()
                        .add(new PaymentApplication(
                                applicationId,
                                id,
                                period,
                                SiteDates.addDays(d("2025-01-25"), (period - 1) * 30),
                                gbp(gross),
                                gbp(Math.round(paid * 0.95))));
                if (random.bool(0.7)) {
                    LocalDate servedOn = SiteDates.addDays(d("2025-01-25"), (period - 1) * 30 + random.between(28, 40));
                    job.paymentNotices().add(new PaymentNotice(applicationId, servedOn, gbp(Math.round(gross * 0.95))));
                }
                paid = gross;
            }

            for (int n = 0; n < 3; n += 1) {
                LocalDate incurredOn = SiteDates.addDays(d("2025-01-05"), random.between(0, 300));
                ContraReason reason = random.pick(CHARGE_REASONS);
                Money amount = gbp(random.between(100, 8000));
                ContraChargeInput input = ContraChargeInput.of(
                                id + "-CC-" + n, id, "a charge against the order", amount, incurredOn)
                        .withReason(reason);
                if (random.bool(0.6)) {
                    input = input.withNotifiedOn(SiteDates.addDays(incurredOn, random.between(0, 20)));
                }
                job.contraCharges().add(ContraCharge.build(input));
            }
        }

        for (int i = 0; i < returns; i += 1) {
            String packageId = "WP-" + padded(random.between(1, packageCount), 4);
            String step = random.pick(RETURN_STEPS);
            LocalDate date = SiteDates.addDays(d("2025-01-06"), random.between(0, 400));
            int count = random.between(2, 8);
            int hours = random.between(6, 12);
            HourKind kind = random.pick(GANG_HOURS);
            int plantHours = random.between(4, 10);
            job.resourceReturns()
                    .add(ResourceReturn.build(ResourceReturnInput.of(
                                    "RR-" + padded(i, 5), packageId, date, "A Kerrigan")
                            .withStep(step)
                            .withLabour(List.of(new ResourceLine("GANG-EARTH", "earthworks gang", count, hours, kind)))
                            .withPlant(List.of(
                                    new ResourceLine("PL-360", "22t excavator", 1, plantHours, HourKind.WORKING)))));
        }
    }
}
