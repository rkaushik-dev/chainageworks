package io.github.rkaushikdev.chainageworks.http;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.sampleContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.ErrorCode;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.CalendarInput;
import io.github.rkaushikdev.chainageworks.domain.time.SiteCalendar;
import io.github.rkaushikdev.chainageworks.http.Problem.Failure;
import io.github.rkaushikdev.chainageworks.ingest.RecordImport;
import io.github.rkaushikdev.chainageworks.progress.ProgressContext;
import io.github.rkaushikdev.chainageworks.reporting.Baseline;
import io.github.rkaushikdev.chainageworks.reporting.ProgressReport;
import io.github.rkaushikdev.chainageworks.reporting.SCurve;
import io.github.rkaushikdev.chainageworks.reporting.StripMap;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.CertificateInput;
import io.github.rkaushikdev.chainageworks.valuation.PeriodSchedule;
import io.github.rkaushikdev.chainageworks.valuation.RetentionTerms;
import io.github.rkaushikdev.chainageworks.valuation.ScheduleInput;
import io.github.rkaushikdev.chainageworks.valuation.ValuationPeriod;
import java.util.Comparator;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DtoTest {

    private static final ProgressContext CONTEXT = sampleContext();
    private static final PeriodSchedule SCHEDULE = PeriodSchedule.build(
            ScheduleInput.of(2025, 1, 6, 25, SiteCalendar.build(CalendarInput.of("C1", "main works"))));
    private static final ValuationPeriod MARCH = SCHEDULE.byNumber(3);

    /** Two trees with the same values, numbers compared by their value rather than by how they are stored. */
    private static final Comparator<JsonNode> SAME_VALUE = (left, right) -> {
        if (left.isNumber() && right.isNumber()) {
            return Double.compare(left.doubleValue(), right.doubleValue());
        }
        return left.equals(right) ? 0 : 1;
    };

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    /** The tree as a client reads it back after it has been sent. */
    private static JsonNode throughText(JsonNode dto) {
        try {
            return Json.parse(Json.stringify(dto));
        } catch (Json.JsonFailure broken) {
            throw new AssertionError("the body did not read back", broken);
        }
    }

    @Nested
    class TheValueShapes {

        @Test
        void sendsMoneyThreeWaysSoNoClientHasToParseASymbol() {
            assertThat(Json.stringify(Dto.money(gbp(1234.56))))
                    .isEqualTo("{\"minor\":123456,\"amount\":1234.56,\"formatted\":\"£1,234.56\"}");
        }

        @Test
        void sendsAQuantityWithItsUnit() {
            assertThat(Json.stringify(Dto.quantity(Quantity.of(12.5, Unit.M3))))
                    .isEqualTo("{\"milli\":12500,\"amount\":12.5,\"unit\":\"m3\",\"formatted\":\"12.50 m3\"}");
        }

        @Test
        void sendsAPercentageAsAPlainNumber() {
            assertThat(Dto.percent(Percent.of(32.5))).isEqualTo(32.5);
        }

        @Test
        void sendsEveryDateOnAPeriod() {
            assertThat(Json.stringify(Dto.period(MARCH)))
                    .isEqualTo("{\"number\":3,\"from\":\"2025-02-26\",\"to\":\"2025-03-25\",\"cutOff\":\"2025-03-25\","
                            + "\"issuedOn\":\"2025-04-03\",\"dueOn\":\"2025-04-17\"}");
        }
    }

    @Nested
    class ProgressReportDto {

        private final ObjectNode dto = Dto.progressReport(ProgressReport.build(CONTEXT, MARCH));

        @Test
        void survivesATripThroughJson() {
            assertThat(Json.stringify(dto)).isNotEmpty();
            assertThat(throughText(dto).equals(SAME_VALUE, dto)).isTrue();
        }

        @Test
        void carriesTheHeadlineFiguresAndThePackages() {
            assertThat(dto.get("billRevision").asText()).isEqualTo("Rev A");
            assertThat(dto.get("earned").get("amount").doubleValue()).isEqualTo(16_152.5);
            assertThat(dto.get("packages")).hasSize(3);
        }

        @Test
        void carriesTheStepDetailWithTheHeldChainage() {
            JsonNode steps = dto.get("packages").get(0).get("steps");
            assertThat(steps).hasSize(4);
            assertThat(steps.get(1).get("builtPercent").doubleValue()).isEqualTo(75);
            assertThat(Json.stringify(steps.get(1).get("heldBack"))).isEqualTo("[\"12+400.000 to 12+600.000\"]");
        }

        @Test
        void carriesTheReconciliationAndTheOverMeasures() {
            assertThat(dto.get("reconciliation")).hasSize(3);
            assertThat(Json.stringify(dto.get("overMeasures"))).isEqualTo("[]");
        }
    }

    @Nested
    class CertificateDto {

        private final ObjectNode dto = Dto.certificate(Certificate.build(
                CertificateInput.of(CONTEXT, gbp(312_190), RetentionTerms.DEFAULT), MARCH, Money.zero(Currency.GBP)));

        @Test
        void walksTheWholeCertificateDownToTheAmountDue() {
            assertThat(dto.get("gross").get("amount").doubleValue()).isEqualTo(16_152.5);
            assertThat(dto.get("amountDue").get("amount").doubleValue()).isEqualTo(15_344.87);
            assertThat(dto.get("retention").get("capReached").booleanValue()).isFalse();
        }

        @Test
        void carriesTheBackingSheetAPackageAtATime() {
            JsonNode packages = dto.get("packages");
            assertThat(packages).hasSize(3);
            assertThat(packages.get(0).get("percent").doubleValue()).isEqualTo(32.5);
            assertThat(packages.get(0).get("allocations")).hasSize(2);
        }

        @Test
        void survivesATripThroughJson() {
            assertThat(throughText(dto).equals(SAME_VALUE, dto)).isTrue();
        }
    }

    @Nested
    class CurveDtoAndStripDto {

        @Test
        void sendsOneRowPerCurvePoint() {
            JsonNode dto = Dto.curve(SCurve.build(CONTEXT, SCHEDULE, Baseline.linear(gbp(120_000), 6)));
            assertThat(dto).hasSize(6);
            assertThat(dto.get(2).get("period").intValue()).isEqualTo(3);
            assertThat(dto.get(2).get("cutOff").asText()).isEqualTo("2025-03-25");
        }

        @Test
        void sendsTheStripAsGlyphsAndAsCells() {
            StripMap map = StripMap.build(CONTEXT, CONTEXT.register().require("WP-01"), d("2025-03-31"), 100_000);
            JsonNode rows = Dto.strip(map).get("rows");
            assertThat(rows.get(1).get("glyphs").asText()).isEqualTo("####oo..");
            assertThat(rows.get(1).get("cells")).hasSize(8);
        }
    }

    @Nested
    class ImportReportDto {

        @Test
        void countsTheRowsAndListsTheIssues() {
            var report = RecordImport.importSurveyRecords(String.join(
                    "\n",
                    "record,package,code,step,from,to,quantity,unit,measured on,booked by",
                    "SR-01,WP-01,E423.1,excavate,12+000,12+200,1800,m3,2025-03-06,J Foreman",
                    "SR-02,WP-01,nonsense,excavate,12+200,12+400,1750,m3,2025-03-13,J Foreman"));
            assertThat(Json.stringify(Dto.importReport(report)))
                    .isEqualTo("{\"rowsRead\":2,\"accepted\":1,\"rejected\":1,\"issues\":[{\"line\":3,"
                            + "\"column\":\"code\",\"severity\":\"error\",\"message\":\"not a bill item code\"}]}");
        }
    }

    @Nested
    class ToFailure {

        @Test
        void mapsEveryCodeToAStatus() {
            assertThat(Problem.statusFor(ErrorCode.BAD_INPUT)).isEqualTo(400);
            assertThat(Problem.statusFor(ErrorCode.NOT_FOUND)).isEqualTo(404);
            assertThat(Problem.statusFor(ErrorCode.CONFLICT)).isEqualTo(409);
            assertThat(Problem.statusFor(ErrorCode.UNSUPPORTED)).isEqualTo(501);
            assertThat(Problem.statusFor(ErrorCode.INTERNAL)).isEqualTo(500);
        }

        @Test
        void turnsADomainErrorIntoAStatusAndABody() {
            Failure failure = Problem.toFailure(new NotFoundException("work package", "WP-99"));
            assertThat(failure.status()).isEqualTo(404);
            assertThat(failure.body())
                    .isEqualTo(new Problem(
                            ErrorCode.NOT_FOUND,
                            "no work package with id WP-99",
                            Map.of("kind", "work package", "id", "WP-99")));
            assertThat(failure.isClientFailure()).isTrue();
        }

        @Test
        void turnsAnythingElseIntoA500() {
            Failure failure = Problem.toFailure(new IllegalArgumentException("not a number"));
            assertThat(failure.status()).isEqualTo(500);
            assertThat(failure.body().code()).isEqualTo(ErrorCode.INTERNAL);
            assertThat(failure.isClientFailure()).isFalse();
        }

        @Test
        void describesItselfForTheRequestLog() {
            assertThat(Problem.toFailure(new BadInputException("nope")).describe())
                    .isEqualTo("400 bad_input: nope");
            assertThat(Problem.toFailure(new ConflictException("locked")).describe())
                    .isEqualTo("409 conflict: locked");
        }
    }
}
