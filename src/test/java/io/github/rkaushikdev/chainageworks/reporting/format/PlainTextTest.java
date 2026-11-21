package io.github.rkaushikdev.chainageworks.reporting.format;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.Baseline;
import io.github.rkaushikdev.chainageworks.reporting.CurvePoint;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.SCurve;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures.Overrides;
import io.github.rkaushikdev.chainageworks.util.Text;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.CertificateInput;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.RetentionTerms;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlainTextTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));
    private static final ValuationPeriod MARCH = SCHEDULE.byNumber(3);
    private static final ProgressReport REPORT = ProgressReport.build(CONTEXT, MARCH);

    private static List<String> lines(String text) {
        return Text.split(text, "\n");
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    @Nested
    class RenderPackageTable {

        private static final String TEXT = PlainText.packageTable(REPORT.lines());

        @Test
        void headsTheColumnsAndRulesThem() {
            List<String> lines = lines(TEXT);
            assertThat(lines.get(0)).matches("package\\s+name\\s+budget\\s+earned\\s+percent\\s+held");
            assertThat(lines.get(1)).containsPattern("^-+");
        }

        @Test
        void givesOneRowPerPackageWithTheMoneyRightAligned() {
            List<String> lines = lines(TEXT);
            assertThat(lines).hasSize(5);
            assertThat(lines.get(2)).contains("WP-01");
            assertThat(lines.get(2)).contains("£16,152.50");
            assertThat(lines.get(2)).contains("32.5%");
        }

        @Test
        void clipsALongPackageName() {
            assertThat(TEXT).contains("…");
        }
    }

    @Nested
    class RenderStepTable {

        @Test
        void showsBuiltAgainstCreditedAndWhatIsHeldBack() {
            List<String> lines = lines(PlainText.stepTable(REPORT.lines().get(0)));
            assertThat(lines.get(0)).matches("step\\s+built\\s+credited\\s+gate\\s+held back");
            assertThat(lines.get(3)).contains("75.0%");
            assertThat(lines.get(3)).contains("50.0%");
            assertThat(lines.get(3)).contains("part open");
            assertThat(lines.get(3)).contains("12+400.000 to 12+600.000");
        }
    }

    @Nested
    class RenderProgressReport {

        private static final String TEXT = PlainText.progressReport(REPORT);

        @Test
        void opensWithThePeriodHeading() {
            assertThat(lines(TEXT).get(0))
                    .isEqualTo("progress report, valuation 3 to 2025-03-25, issued 2025-04-03, due 2025-04-17");
        }

        @Test
        void carriesTheHeadlineFigures() {
            assertThat(TEXT).containsPattern("bill revision +Rev A");
            assertThat(TEXT).containsPattern("earned +£16,152\\.50");
            assertThat(TEXT).containsPattern("working days +20");
        }

        @Test
        void hasASectionForTheWorkThatCannotBePaidFor() {
            assertThat(TEXT).contains("work built and not payable");
            assertThat(TEXT).contains("hold points outstanding: IP-E1, IP-E2, IP-P1");
        }

        @Test
        void listsTheReconciliationProblems() {
            assertThat(TEXT).contains("to look at");
            assertThat(TEXT).contains("E423.1 is allocated");
        }

        @Test
        void leavesOutTheSectionsThatHaveNothingInThem() {
            ProgressReport clean = ProgressReport.build(
                    sampleContext(Overrides.NONE
                            .withSignOffs(List.of())
                            .withNcrs(List.of())
                            .withRecords(List.of())),
                    MARCH);
            assertThat(PlainText.progressReport(clean)).doesNotContain("work built and not payable");
            assertThat(PlainText.progressReport(clean)).doesNotContain("days lost");
        }

        @Test
        void listsTheDaysLostWhenTheDiaryHasAny() {
            ProgressReport wet = ProgressReport.build(
                    CONTEXT.withCalendar(SiteCalendar.build(CalendarInput.of("C2", "wet march")
                            .withStanddowns(
                                    List.of(new Standdown(d("2025-03-05"), StanddownReason.WEATHER, "rain all day"))))),
                    MARCH);
            String text = PlainText.progressReport(wet);
            assertThat(text).contains("days lost");
            assertThat(text).contains("2025-03-05  weather  rain all day");
        }
    }

    @Nested
    class RenderCertificate {

        private static final Certificate CERTIFICATE = Certificate.build(
                CertificateInput.of(CONTEXT, gbp(312_190), RetentionTerms.DEFAULT), MARCH, Money.zero(Currency.GBP));
        private static final String TEXT = PlainText.certificate(CERTIFICATE);

        @Test
        void headsThePageWithTheCertificateNumber() {
            assertThat(lines(TEXT).get(0)).isEqualTo("payment certificate 3");
        }

        @Test
        void carriesTheDatesThatMatterToAPayer() {
            assertThat(TEXT).containsPattern("valuation to +2025-03-25");
            assertThat(TEXT).containsPattern("payment due +2025-04-17");
        }

        @Test
        void walksDownFromMeasuredWorkToTheAmountDue() {
            assertThat(TEXT).containsPattern("measured work +£16,152\\.50");
            assertThat(TEXT).containsPattern("less retention +£807\\.63");
            assertThat(TEXT).containsPattern("amount due this certificate +£15,344\\.87");
        }

        @Test
        void saysSoWhenRetentionHasCappedOut() {
            Certificate capped = Certificate.build(
                    CertificateInput.of(CONTEXT, gbp(1000), RetentionTerms.DEFAULT), MARCH, Money.zero(Currency.GBP));
            assertThat(PlainText.certificate(capped)).contains("retention has reached its cap");
            assertThat(TEXT).doesNotContain("retention has reached its cap");
        }
    }

    @Nested
    class RenderCurve {

        @Test
        void givesOneRowPerPeriod() {
            List<CurvePoint> points = SCurve.build(CONTEXT, SCHEDULE, Baseline.linear(gbp(120_000), 6));
            List<String> lines = lines(PlainText.curve(points));
            assertThat(lines).hasSize(8);
            assertThat(lines.get(0)).matches("period\\s+cut off\\s+planned\\s+earned\\s+variance\\s+earned %");
            assertThat(lines.get(4)).contains("-£43,847.50");
        }
    }

    @Nested
    class RenderReconciliation {

        @Test
        void tablesTheProblems() {
            List<String> lines = lines(PlainText.reconciliation(REPORT));
            assertThat(lines.get(0)).matches("code\\s+problem\\s+billed\\s+allocated");
            assertThat(lines).hasSize(5);
            assertThat(lines.get(3)).contains("under allocated");
        }

        @Test
        void saysSoWhenThereIsNothingToReport() {
            ProgressReport clean = ProgressReport.build(sampleContext(Overrides.NONE.withRecords(List.of())), MARCH);
            ProgressReport empty = new ProgressReport(
                    clean.period(),
                    clean.billRevision(),
                    clean.lines(),
                    clean.budget(),
                    clean.earned(),
                    clean.heldValue(),
                    clean.workingDays(),
                    clean.productiveDays(),
                    clean.standdowns(),
                    List.of(),
                    clean.overMeasures());
            assertThat(PlainText.reconciliation(empty)).isEqualTo("the packages reconcile against the bill");
        }
    }
}
