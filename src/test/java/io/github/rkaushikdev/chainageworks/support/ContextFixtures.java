package io.github.rkaushikdev.chainageworks.support;

import static io.github.rkaushikdev.chainageworks.support.SurveyFixtures.records;

import io.github.rkaushikdev.chainageworks.domain.bill.BillHistory;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.credit.CreditCatalogue;
import io.github.rkaushikdev.chainageworks.domain.credit.ProfileRegistry;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPlan;
import io.github.rkaushikdev.chainageworks.domain.itp.InspectionPoint;
import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrInput;
import io.github.rkaushikdev.chainageworks.domain.itp.PointType;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffInput;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffOutcome;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.progress.ProgressContextInput;
import io.github.rkaushikdev.chainageworks.support.SurveyFixtures.RecordSpec;
import java.time.LocalDate;
import java.util.List;

/** A small but complete progress context: the sample bill, packages, survey book and gates. */
public final class ContextFixtures {

    private ContextFixtures() {}

    public static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    public static long at(String text) {
        return Stations.parse(text);
    }

    public static ChainageRange range(String from, String to) {
        return ChainageRange.of(at(from), at(to));
    }

    public static final InspectionPlan EARTHWORKS_PLAN = InspectionPlan.build(
            "ITP-EW",
            "earthworks inspection and test plan",
            "bulk-earthworks",
            List.of(
                    InspectionPoint.of("IP-E1", "formation level and tolerance", PointType.HOLD, "excavate")
                            .withDiscipline("geotech"),
                    InspectionPoint.of("IP-E2", "layer compaction density", PointType.HOLD, "place")
                            .withDiscipline("geotech"),
                    InspectionPoint.of("IP-E3", "topsoil stockpile location", PointType.WITNESS, "strip")),
            CreditCatalogue.BULK_EARTHWORKS);

    public static final InspectionPlan PAVEMENT_PLAN = InspectionPlan.build(
            "ITP-PAV",
            "pavement inspection and test plan",
            "pavement",
            List.of(InspectionPoint.of("IP-P1", "sub base level", PointType.HOLD, "subbase")),
            CreditCatalogue.PAVEMENT);

    public static BillHistory sampleHistory() {
        return BillHistory.build(List.of(
                new BillRevision("Rev A", d("2025-01-01"), BillFixtures.sampleBill(), "contract bill at award")));
    }

    public static SignOff signOff(String id, String pointId, String packageId, String from, String to, String on) {
        return SignOff.build(SignOffInput.of(id, pointId, packageId, range(from, to), d(on), "R Engineer"));
    }

    public static SignOff signOff(
            String id, String pointId, String packageId, String from, String to, String on, SignOffOutcome outcome) {
        return SignOff.build(SignOffInput.of(id, pointId, packageId, range(from, to), d(on), "R Engineer")
                .withOutcome(outcome));
    }

    public static List<SurveyRecord> sampleRecords() {
        return records(
                RecordSpec.of("SR-01", "E423.1", "strip", "12+000", "12+800", 640, Unit.M3, "2025-03-03")
                        .inPackage("WP-01"),
                RecordSpec.of("SR-02", "E423.1", "excavate", "12+000", "12+600", 5200, Unit.M3, "2025-03-10")
                        .inPackage("WP-01"),
                RecordSpec.of("SR-03", "E532.1", "place", "12+000", "12+400", 2400, Unit.M3, "2025-03-20")
                        .inPackage("WP-01"));
    }

    public static List<SignOff> sampleSignOffs() {
        return List.of(
                signOff("SO-01", "IP-E1", "WP-01", "12+000", "12+400", "2025-03-12"),
                signOff("SO-02", "IP-E2", "WP-01", "12+000", "12+200", "2025-03-22"));
    }

    public static List<Ncr> sampleNcrs() {
        return List.of(Ncr.build(NcrInput.of(
                        "NCR-01",
                        "WP-01",
                        range("12+100", "12+150"),
                        d("2025-03-25"),
                        "layer thickness over the specified 250 mm")
                .withStep("place")));
    }

    /**
     * What to put in the sample context instead of its own records, sign offs, reports or plans.
     * A null leaves the sample's own.
     */
    public record Overrides(
            List<SurveyRecord> records, List<SignOff> signOffs, List<Ncr> ncrs, List<InspectionPlan> plans) {

        public static final Overrides NONE = new Overrides(null, null, null, null);

        public Overrides withRecords(List<SurveyRecord> other) {
            return new Overrides(other, signOffs, ncrs, plans);
        }

        public Overrides withSignOffs(List<SignOff> other) {
            return new Overrides(records, other, ncrs, plans);
        }

        public Overrides withNcrs(List<Ncr> other) {
            return new Overrides(records, signOffs, other, plans);
        }

        public Overrides withPlans(List<InspectionPlan> other) {
            return new Overrides(records, signOffs, ncrs, other);
        }
    }

    public static ProgressContext sampleContext() {
        return sampleContext(Overrides.NONE);
    }

    public static ProgressContext sampleContext(Overrides overrides) {
        SiteCalendar calendar = SiteCalendar.build(CalendarInput.of("C1", "main works"));
        return ProgressContext.build(ProgressContextInput.of(
                        sampleHistory(),
                        PackageRegister.build(PackageFixtures.samplePackages()),
                        ProfileRegistry.standard(),
                        calendar)
                .withPlans(overrides.plans() == null ? List.of(EARTHWORKS_PLAN, PAVEMENT_PLAN) : overrides.plans())
                .withSignOffs(overrides.signOffs() == null ? sampleSignOffs() : overrides.signOffs())
                .withNcrs(overrides.ncrs() == null ? sampleNcrs() : overrides.ncrs())
                .withRecords(overrides.records() == null ? sampleRecords() : overrides.records()));
    }
}
