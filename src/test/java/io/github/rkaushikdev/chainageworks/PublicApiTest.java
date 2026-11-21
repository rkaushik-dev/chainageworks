package io.github.rkaushikdev.chainageworks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Doctor;
import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemInput;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageAllocation;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackageInput;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.store.JobSettingsInput;
import io.github.rkaushikdev.chainageworks.store.codec.JobCodec;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The public surface: the top-level types callers may depend on.
 *
 * <p>Anything public is something callers may depend on, and everything else is free to change: the
 * split between the two is the only thing that makes a version number mean anything. The list is
 * named here as well as by the modifiers on the types, on purpose. A test that read the public types
 * and checked them against themselves would prove nothing; a test that carries its own list turns any
 * accidental change of visibility into a failure, and any deliberate one into an edit somebody has to
 * make twice.
 */
class PublicApiTest {

    private static final String ROOT = PublicApiTest.class.getPackageName();

    /** Every public top-level type, named from the root package, grouped the way the domain is layered. */
    private static final List<String> EXPECTED = List.of(
            // errors and results
            "BadInputException",
            "ConflictException",
            "CwException",
            "Details",
            "ErrorCode",
            "NotFoundException",
            "Result",
            "UnsupportedException",
            // units
            "domain.money.Apportion",
            "domain.money.Currency",
            "domain.money.Money",
            "domain.money.Percent",
            "domain.money.Rate",
            "domain.quantity.Dimension",
            "domain.quantity.Quantity",
            "domain.quantity.Unit",
            // where and when
            "domain.chainage.Alignment",
            "domain.chainage.ChainageRange",
            "domain.chainage.Stations",
            "domain.time.CalendarInput",
            "domain.time.DateRange",
            "domain.time.SiteCalendar",
            "domain.time.SiteDates",
            "domain.time.Standdown",
            "domain.time.StanddownReason",
            // the bill, the packages and the rules of credit
            "domain.bill.Bill",
            "domain.bill.BillDiff",
            "domain.bill.BillHistory",
            "domain.bill.BillItem",
            "domain.bill.BillItemInput",
            "domain.bill.BillItemKind",
            "domain.bill.BillRevision",
            "domain.bill.ItemCode",
            "domain.packages.PackageAllocation",
            "domain.packages.PackageRegister",
            "domain.packages.WorkPackage",
            "domain.packages.WorkPackageInput",
            "domain.credit.CreditCatalogue",
            "domain.credit.CreditProfile",
            "domain.credit.CreditStep",
            "domain.credit.ProfileRegistry",
            "domain.credit.StepProgress",
            // what was built, and what may be credited for it
            "domain.survey.MeasurementMethod",
            "domain.survey.RecordQuery",
            "domain.survey.SurveyRecord",
            "domain.survey.SurveyRecordInput",
            "domain.survey.ToleranceSet",
            "domain.itp.GateState",
            "domain.itp.InspectionPlan",
            "domain.itp.InspectionPoint",
            "domain.itp.Ncr",
            "domain.itp.NcrInput",
            "domain.itp.NcrStatus",
            "domain.itp.PointType",
            "domain.itp.SignOff",
            "domain.itp.SignOffInput",
            "domain.itp.SignOffOutcome",
            "domain.itp.StepGate",
            "progress.Lookahead",
            "progress.LookaheadInput",
            "progress.OutputAllowance",
            "progress.PackageEarning",
            "progress.PackageOutput",
            "progress.ProgressContext",
            "progress.ProgressContextInput",
            "progress.StepLookahead",
            "progress.StepProgressDetail",
            // the commercial registers
            "domain.variation.ValuationBasis",
            "domain.variation.Variation",
            "domain.variation.VariationApplication",
            "domain.variation.VariationInput",
            "domain.variation.VariationItem",
            "domain.variation.VariationStatus",
            "domain.daywork.DayworkAdditions",
            "domain.daywork.DayworkRate",
            "domain.daywork.DayworkSchedule",
            "domain.daywork.DayworkScheduleInput",
            "domain.daywork.DayworkSheet",
            "domain.daywork.DayworkSheetInput",
            "domain.daywork.LabourEntry",
            "domain.daywork.MaterialEntry",
            "domain.daywork.PlantEntry",
            "domain.delay.BarReason",
            "domain.delay.CompletionTerms",
            "domain.delay.DelayCause",
            "domain.delay.DelayEvent",
            "domain.delay.DelayEventInput",
            "domain.delay.Entitlement",
            "domain.defects.Attribution",
            "domain.defects.CertificateBlock",
            "domain.defects.Defect",
            "domain.defects.DefectInput",
            "domain.defects.DefectSeverity",
            "domain.earthworks.CrossSection",
            "domain.earthworks.HaulAllocation",
            "domain.earthworks.HaulLink",
            "domain.earthworks.HaulRun",
            "domain.earthworks.HaulSite",
            "domain.earthworks.Movement",
            "domain.earthworks.SiteKind",
            "domain.fluctuation.AdjustmentFactor",
            "domain.fluctuation.AdjustmentTerms",
            "domain.fluctuation.IndexReading",
            "domain.fluctuation.IndexSeries",
            "domain.fluctuation.PriceAdjustment",
            "domain.fluctuation.Weighting",
            "domain.resource.HourKind",
            "domain.resource.ResourceLine",
            "domain.resource.ResourceReturn",
            "domain.resource.ResourceReturnInput",
            "domain.resource.Shift",
            "domain.risk.EarlyWarning",
            "domain.risk.EarlyWarningInput",
            "domain.risk.RiskOwner",
            "domain.risk.RiskState",
            "domain.subcontract.ContraCharge",
            "domain.subcontract.ContraChargeInput",
            "domain.subcontract.ContraReason",
            "domain.subcontract.NotifiedBasis",
            "domain.subcontract.PayLessNotice",
            "domain.subcontract.PaymentApplication",
            "domain.subcontract.PaymentNotice",
            "domain.subcontract.PaymentTerms",
            "domain.subcontract.Subcontract",
            "domain.subcontract.SubcontractInput",
            // what it is worth
            "valuation.BondPosition",
            "valuation.CashFlow",
            "valuation.CashFlowInput",
            "valuation.CashPoint",
            "valuation.Certificate",
            "valuation.CertificateInput",
            "valuation.FinalAccount",
            "valuation.MaterialsInput",
            "valuation.MaterialsOnSite",
            "valuation.NotClaimableReason",
            "valuation.Outturn",
            "valuation.OutturnInput",
            "valuation.PeriodSchedule",
            "valuation.RetentionBond",
            "valuation.RetentionBondInput",
            "valuation.RetentionQuery",
            "valuation.RetentionState",
            "valuation.RetentionTerms",
            "valuation.ScheduleInput",
            "valuation.ValuationPeriod",
            // reading it in and setting it out
            "io.Csv",
            "ingest.BillImport",
            "ingest.DiaryImport",
            "ingest.ImportIssue",
            "ingest.ImportReport",
            "ingest.IndexImport",
            "ingest.InspectionImport",
            "ingest.IssueSeverity",
            "ingest.PaymentImport",
            "ingest.RecordImport",
            "ingest.ResourceImport",
            "reporting.Baseline",
            "reporting.CellState",
            "reporting.CsvExport",
            "reporting.CurvePoint",
            "reporting.DefectsReport",
            "reporting.DelayReport",
            "reporting.HaulReport",
            "reporting.LookaheadReport",
            "reporting.OutputReport",
            "reporting.OutturnReport",
            "reporting.ProgressReport",
            "reporting.RevisionReport",
            "reporting.RiskReport",
            "reporting.SCurve",
            "reporting.StripMap",
            "reporting.SubcontractReport",
            "reporting.VariationReport",
            "reporting.format.Align",
            "reporting.format.Column",
            "reporting.format.Markdown",
            "reporting.format.PlainText",
            "reporting.format.Table",
            "reporting.format.TableOptions",
            "util.Collation",
            "util.Json",
            "util.Numbers",
            "util.Patterns",
            "util.Text",
            // the application
            "store.FileStore",
            "store.Job",
            "store.JobSettings",
            "store.JobSettingsInput",
            "store.Repository",
            "store.codec.JobCodec",
            "config.Config",
            "config.ConfigOverrides",
            "config.LogLevel",
            "app.Diagnosis",
            "app.Doctor",
            "app.Finding",
            "app.FindingLevel",
            "app.LoadBillOptions",
            "app.MonthEndPack",
            "app.PackFile",
            "app.Service",
            "app.Startup",
            "http.ChainageworksApplication",
            "http.Dto",
            "cli.CliDeps",
            "cli.Cw",
            "cli.Program");

    /** The type {@code name} from the root package, when there is one. */
    private static Optional<Class<?>> resolve(String name) {
        try {
            return Optional.of(Class.forName(ROOT + "." + name, false, PublicApiTest.class.getClassLoader()));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        }
    }

    /** The names in {@code names} that are not a public type. */
    private static List<String> missingOrHidden(List<String> names) {
        return names.stream()
                .filter(name -> resolve(name)
                        .map(type -> !Modifier.isPublic(type.getModifiers()))
                        .orElse(true))
                .toList();
    }

    /** The directory the main classes were compiled into, found from where one of them was loaded. */
    private static Path mainClasses() throws URISyntaxException {
        Path location = Path.of(Service.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        assertThat(location).as("the main classes are read from a directory").isDirectory();
        return location;
    }

    /** Every top-level type compiled into the main classes, named from the root package. */
    private static List<String> compiledTopLevelTypes() throws IOException, URISyntaxException {
        Path root = mainClasses().resolve(ROOT.replace('.', '/'));
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file)
                            .toString()
                            .replace(file.getFileSystem().getSeparator(), "/"))
                    .filter(file -> file.endsWith(".class") && !file.contains("$"))
                    .map(file ->
                            file.substring(0, file.length() - ".class".length()).replace('/', '.'))
                    .filter(name -> !name.endsWith("package-info"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isPublic(String name) {
        return resolve(name).map(type -> Modifier.isPublic(type.getModifiers())).orElseThrow();
    }

    /** True when {@code type} declares a public method, field or constructor a caller can reach. */
    private static boolean exposesAPublicMember(Class<?> type) {
        return Stream.of(type.getDeclaredMethods())
                        .anyMatch(method -> !method.isSynthetic() && Modifier.isPublic(method.getModifiers()))
                || Stream.of(type.getDeclaredFields()).anyMatch(field -> Modifier.isPublic(field.getModifiers()))
                || Stream.of(type.getDeclaredConstructors())
                        .anyMatch(constructor -> Modifier.isPublic(constructor.getModifiers()));
    }

    /** A type with nothing on it a caller could use, so the member check has something to refuse. */
    private static final class NothingToOffer {

        private NothingToOffer() {}
    }

    private static Job aShortJob() {
        return Job.create(JobSettingsInput.of("A1", "a short job", Money.fromMajor(100_000, Currency.GBP)));
    }

    @Nested
    class ThePublicEntryPoint {

        @Test
        void exportsEverythingTheListNames() {
            assertThat(EXPECTED).doesNotHaveDuplicates();
            assertThat(missingOrHidden(EXPECTED)).isEmpty();
            // Names that must not pass: a type that is there but package-private, and one that is not
            // there at all.
            assertThat(missingOrHidden(List.of("http.JobRoutes", "domain.money.Shilling")))
                    .containsExactly("http.JobRoutes", "domain.money.Shilling");
        }

        @Test
        void exportsNothingTheListDoesNotName() throws IOException, URISyntaxException {
            List<String> compiled = compiledTopLevelTypes();
            // The walk has to have read the classes: a public type and a package-private one it must
            // have seen and told apart.
            assertThat(compiled).hasSizeGreaterThan(EXPECTED.size()).contains("app.Service", "http.JobRoutes");
            assertThat(isPublic("app.Service")).isTrue();
            assertThat(isPublic("http.JobRoutes")).isFalse();

            List<String> extra = new ArrayList<>();
            for (String name : compiled) {
                if (isPublic(name) && !EXPECTED.contains(name)) {
                    extra.add(name);
                }
            }
            assertThat(extra).isEmpty();
        }

        @Test
        void exportsAFunctionOrAValueForEveryName() {
            assertThat(exposesAPublicMember(NothingToOffer.class)).isFalse();
            for (String name : EXPECTED) {
                assertThat(resolve(name).map(PublicApiTest::exposesAPublicMember))
                        .as(name)
                        .contains(true);
            }
        }
    }

    @Nested
    class TheEntryPointIsEnoughToValueAJob {

        @Test
        void buildsAJobABillAndACertificateWithoutReachingPastIt() {
            Job job = aShortJob();

            job.alignments()
                    .add(Alignment.build("A1", "main carriageway", Stations.parse("0+000"), Stations.parse("1+000")));
            job.billRevisions()
                    .add(new BillRevision(
                            "Rev A",
                            SiteDates.parse("2025-01-01"),
                            Bill.build(
                                    "Rev A",
                                    "bill of quantities",
                                    Currency.GBP,
                                    List.of(BillItem.build(BillItemInput.of(
                                            ItemCode.parse("E423.1"),
                                            "excavation",
                                            Rate.of(5, Currency.GBP, Unit.M3),
                                            Quantity.of(2000, Unit.M3))))),
                            "award"));
            job.packages()
                    .add(WorkPackage.build(new WorkPackageInput(
                            "WP-01",
                            "earthworks",
                            "A1",
                            ChainageRange.of(Stations.parse("0+000"), Stations.parse("1+000")),
                            "section 1",
                            "single-step",
                            List.of(new PackageAllocation(ItemCode.parse("E423.1"), Quantity.of(2000, Unit.M3))))));

            Service service = new Service(job, Config.DEFAULT);
            Certificate certificate = service.certificateFor(1);

            assertThat(certificate.period().number()).isEqualTo(1);
            assertThat(certificate.gross().major()).isZero();
            assertThat(certificate.lines().size()).isGreaterThan(5);
            assertThat(job.blockers()).isEmpty();

            // Everything above came off the public list and nothing else.
            List<String> used = Stream.of(
                            Job.class,
                            JobSettingsInput.class,
                            Money.class,
                            Currency.class,
                            Alignment.class,
                            Stations.class,
                            BillRevision.class,
                            SiteDates.class,
                            Bill.class,
                            BillItem.class,
                            BillItemInput.class,
                            ItemCode.class,
                            Rate.class,
                            Unit.class,
                            Quantity.class,
                            WorkPackage.class,
                            WorkPackageInput.class,
                            ChainageRange.class,
                            PackageAllocation.class,
                            Service.class,
                            Config.class,
                            Certificate.class)
                    .map(type -> type.getName().substring(ROOT.length() + 1))
                    .toList();
            assertThat(EXPECTED).containsAll(used);
        }

        @Test
        void roundTripsThatJobThroughTheCodecOffTheEntryPoint() throws Json.JsonFailure {
            Job again = JobCodec.fromJson(Json.parse(Json.stringify(JobCodec.toJson(aShortJob()))));
            assertThat(again.settings().id()).isEqualTo("A1");
            assertThat(again.settings().contractSum().major()).isEqualTo(100_000);
        }

        @Test
        void reportsWhatAnEmptyJobIsMissing() {
            Job job = aShortJob();
            assertThat(job.blockers()).hasSize(3);
            assertThat(Doctor.diagnose(job).isHealthy()).isFalse();
        }
    }
}
