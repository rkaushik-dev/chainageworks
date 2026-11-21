package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.config.ConfigOverrides;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.survey.SurveyRecord;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.ingest.ImportReport;
import io.github.rkaushikdev.chainageworks.reporting.CurvePoint;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.StripMap;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceTest {

    private static Service service() {
        return new Service(sampleJob(), Config.DEFAULT);
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    @Nested
    class ScheduleOf {

        @Test
        void anchorsOnTheMonthTheFirstBillTookEffect() {
            PeriodSchedule schedule = service().schedule(6);
            assertThat(SiteDates.format(schedule.periods().get(0).cutOff())).isEqualTo("2025-01-24");
            assertThat(schedule.periods()).hasSize(6);
        }

        @Test
        void numbersPeriodsTheSameHoweverFarOutItIsAskedFor() {
            assertThat(SiteDates.format(service().period(3).cutOff())).isEqualTo("2025-03-25");
            assertThat(SiteDates.format(service().schedule(60).periods().get(2).cutOff()))
                    .isEqualTo("2025-03-25");
        }

        @Test
        void refusesAValuationNumberThatIsNotAWholeNumberFromOne() {
            assertThatThrownBy(() -> service().period(0)).hasMessageContaining("counts from one");
            assertThatThrownBy(() -> service().period(Service.valuationNumber(1.5)))
                    .isInstanceOf(BadInputException.class);
        }
    }

    @Nested
    class ProgressFor {

        @Test
        void reportsThePeriodAskedFor() {
            ProgressReport report = service().progressFor(3);
            assertThat(report.period().number()).isEqualTo(3);
            assertThat(report.earned().major()).isEqualTo(16_152.5);
        }

        @Test
        void refusesWhenStrictReconciliationIsOnAndTheRegisterDoesNotBalance() {
            Service strict = new Service(
                    sampleJob(), Config.DEFAULT.merge(ConfigOverrides.none().withStrictReconciliation(true)));
            assertThatThrownBy(() -> strict.progressFor(3)).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> strict.progressFor(3)).hasMessageContaining("do not reconcile");
        }
    }

    @Nested
    class CertificateFor {

        @Test
        void runsTheSeriesFromPeriodOneSoThePreviousFigureIsRight() {
            Certificate certificate = service().certificateFor(3);
            assertThat(certificate.period().number()).isEqualTo(3);
            assertThat(certificate.previouslyCertified().minor()).isZero();
            assertThat(certificate.amountDue().major()).isEqualTo(15_344.87);
        }

        @Test
        void paysNothingInTheMonthAfterBecauseNothingMoreWasMeasured() {
            assertThat(service().certificateFor(4).amountDue().minor()).isZero();
            assertThat(service().certificateFor(4).netValuation().major()).isEqualTo(15_344.87);
        }

        @Test
        void takesTheMaterialsOnTheJobIntoTheGross() {
            Service withMaterials = service();
            assertThat(withMaterials.job().materials().count()).isZero();
            assertThat(withMaterials.certificateFor(3).materialsOnSite().major())
                    .isZero();
        }
    }

    @Nested
    class CurveFor {

        @Test
        void givesOnePointPerPeriodAgainstAnSCurveBaseline() {
            List<CurvePoint> points = service().curveFor(6);
            assertThat(points).hasSize(6);
            assertThat(points.get(5).planned().major()).isEqualTo(312_190);
            assertThat(points.get(2).earned().major()).isEqualTo(16_152.5);
        }
    }

    @Nested
    class StripFor {

        @Test
        void drawsTheStripForAPackageAtACutOff() {
            StripMap map = service().stripFor("WP-01", 3);
            assertThat(map.packageId()).isEqualTo("WP-01");
            assertThat(map.rows()).hasSize(4);
        }

        @Test
        void refusesAPackageTheJobDoesNotCarry() {
            assertThatThrownBy(() -> service().stripFor("WP-99", 3))
                    .hasMessageContaining("no work package with id WP-99");
        }
    }

    @Nested
    class LoadingSitePaperwork {

        private static final String RECORD_SHEET = lines(
                "record,package,code,step,from,to,quantity,unit,measured on,booked by",
                "SR-10,WP-02,E423.1,excavate,12+800,13+000,900,m3,2025-03-24,J Foreman");

        private static final String WITH_A_BAD_ROW = RECORD_SHEET + "\nSR-11,WP-02,nonsense,excavate,1,2,3,m3,x,y";

        @Test
        void addsCleanRecordsToTheJob() {
            Service s = service();
            ImportReport<SurveyRecord> report = s.loadRecords(RECORD_SHEET);
            assertThat(report.hasErrors()).isFalse();
            assertThat(s.job().records().count()).isEqualTo(4);
            assertThat(s.job().records().require("SR-10").packageId()).isEqualTo("WP-02");
        }

        @Test
        void appliesNothingWhenARowFailed() {
            Service s = service();
            ImportReport<SurveyRecord> report = s.loadRecords(WITH_A_BAD_ROW);
            assertThat(report.hasErrors()).isTrue();
            assertThat(s.job().records().count()).isEqualTo(3);
        }

        @Test
        void appliesTheCleanRowsWhenAskedTo() {
            Service s = service();
            s.loadRecords(WITH_A_BAD_ROW, true);
            assertThat(s.job().records().count()).isEqualTo(4);
        }

        @Test
        void addsSignOffsAndReports() {
            Service s = service();
            s.loadSignOffs(lines(
                    "sign off,point,package,from,to,signed on,by",
                    "SO-09,IP-E1,WP-02,12+800,13+000,2025-03-20,R Engineer"));
            assertThat(s.job().signOffs().count()).isEqualTo(3);

            s.loadNcrs(lines(
                    "ncr,package,from,to,raised on,description", "NCR-09,WP-02,12+800,12+900,2025-03-21,rutting"));
            assertThat(s.job().ncrs().count()).isEqualTo(2);
        }

        @Test
        void addsStanddownsAndHolidaysToTheCalendar() {
            Service s = service();
            s.loadDiary(lines("date,reason", "2025-03-05,rain"));
            assertThat(s.job().standdowns()).hasSize(1);

            s.loadHolidays(lines("date", "2025-04-18", "2025-04-21"));
            assertThat(s.job().holidays()).hasSize(2);
            s.loadHolidays(lines("date", "2025-04-18"));
            assertThat(s.job().holidays()).hasSize(2);
        }
    }

    @Nested
    class LoadBill {

        @Test
        void addsANewRevisionRatherThanReplacingTheLastOne() {
            Service s = service();
            ImportReport<BillItem> report = s.loadBill(
                    "code,description,unit,rate,quantity\nE423.1,excavation,m3,5.2,12400",
                    LoadBillOptions.of("Rev B", d("2025-04-01")).withNote("rate agreed"));
            assertThat(report.hasErrors()).isFalse();
            assertThat(s.job().billRevisions().count()).isEqualTo(2);
            assertThat(s.job().billRevisions().require("Rev A").bill().total().major())
                    .isEqualTo(312_190);
            assertThat(s.job().billRevisions().require("Rev B").bill().total().major())
                    .isEqualTo(64_480);
        }

        @Test
        void refusesAReferenceThatIsAlreadyUsed() {
            Service s = service();
            assertThatThrownBy(() -> s.loadBill(
                            "code,description,unit,rate,quantity\nE423.1,excavation,m3,5.2,1",
                            LoadBillOptions.of("Rev A", d("2025-04-01"))))
                    .hasMessageContaining("already there");
        }

        @Test
        void addsNothingWhenTheSheetHasAnError() {
            Service s = service();
            s.loadBill(
                    "code,description,unit,rate,quantity\nnonsense,excavation,m3,5.2,1",
                    LoadBillOptions.of("Rev B", d("2025-04-01")));
            assertThat(s.job().billRevisions().count()).isEqualTo(1);
        }
    }
}
