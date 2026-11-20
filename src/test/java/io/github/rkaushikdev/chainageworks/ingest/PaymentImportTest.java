package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PayLessNotice;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentApplication;
import io.github.rkaushikdev.chainageworks.domain.subcontract.PaymentNotice;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentImportTest {

    private static final Currency GBP = Currency.GBP;

    private static final String APPLICATION_CSV = String.join(
            "\n",
            "application,subcontract,valuation,applied on,gross,previously paid",
            "SA-01,SC-EARTH,1,2025-01-25,40000.00,0.00",
            "SA-02,SC-EARTH,2,2025-02-25,90000.00,36100.00",
            "SA-03,SC-FENCE,2,2025-02-25,12500.50,");

    @Nested
    class ImportApplications {

        @Test
        void readsACleanSchedule() {
            ImportReport<PaymentApplication> report = PaymentImport.importApplications(APPLICATION_CSV, GBP);
            assertThat(report.rowsRead()).isEqualTo(3);
            assertThat(report.rowsAccepted()).isEqualTo(3);
            assertThat(report.issues()).isEmpty();
        }

        @Test
        void readsTheFiguresOffEachRow() {
            PaymentApplication first = PaymentImport.importApplications(APPLICATION_CSV, GBP)
                    .imported()
                    .get(0);
            assertThat(first.id()).isEqualTo("SA-01");
            assertThat(first.subcontractId()).isEqualTo("SC-EARTH");
            assertThat(first.periodNumber()).isEqualTo(1);
            assertThat(SiteDates.format(first.appliedOn())).isEqualTo("2025-01-25");
            assertThat(first.gross().major()).isEqualTo(40_000);
        }

        @Test
        void takesAnEmptyPreviouslyPaidAsNothingPaid() {
            ImportReport<PaymentApplication> report = PaymentImport.importApplications(APPLICATION_CSV, GBP);
            assertThat(report.imported().get(2).previouslyPaid().minor()).isZero();
            assertThat(report.imported().get(2).gross().major()).isEqualTo(12_500.5);
        }

        @Test
        void refusesAFileMissingAColumnItNeeds() {
            ImportReport<PaymentApplication> report =
                    PaymentImport.importApplications("application,subcontract\nSA-01,SC-EARTH", GBP);
            assertThat(report.rowsAccepted()).isZero();
            assertThat(report.errors().get(0).message()).contains("valuation");
        }

        @Test
        void rejectsTheRowWithTheUnreadableDateAndKeepsTheRest() {
            String text = String.join(
                    "\n",
                    "application,subcontract,valuation,applied on,gross",
                    "SA-01,SC-EARTH,1,25/01/2025,40000.00",
                    "SA-02,SC-EARTH,2,2025-02-25,90000.00");
            ImportReport<PaymentApplication> report = PaymentImport.importApplications(text, GBP);
            assertThat(report.rowsAccepted()).isEqualTo(1);
            assertThat(report.rowsRejected()).isEqualTo(1);
            assertThat(report.errors().get(0).line()).contains(2);
        }

        @Test
        void rejectsTheSecondRowThatCarriesAnApplicationIdAlreadyUsed() {
            String text = String.join(
                    "\n",
                    "application,subcontract,valuation,applied on,gross",
                    "SA-01,SC-EARTH,1,2025-01-25,40000.00",
                    "SA-01,SC-EARTH,2,2025-02-25,90000.00");
            ImportReport<PaymentApplication> report = PaymentImport.importApplications(text, GBP);
            assertThat(report.rowsAccepted()).isEqualTo(1);
            assertThat(report.errors().get(0).message()).contains("appears twice");
        }

        @Test
        void rejectsAValuationNumberThatIsNotANumber() {
            String text = String.join(
                    "\n",
                    "application,subcontract,valuation,applied on,gross",
                    "SA-01,SC-EARTH,first,2025-01-25,40000.00");
            assertThat(PaymentImport.importApplications(text, GBP).rowsAccepted())
                    .isZero();
        }
    }

    @Nested
    class ImportNotices {

        private final String csv = String.join(
                "\n", "application,served on,valued", "SA-01,2025-02-27,38000.00", "SA-02,2025-03-28,85000.00");

        @Test
        void readsWhatThePayerSaidEachApplicationWasWorth() {
            ImportReport<PaymentNotice> report = PaymentImport.importNotices(csv, GBP);
            assertThat(report.rowsAccepted()).isEqualTo(2);
            assertThat(report.imported().get(1).applicationId()).isEqualTo("SA-02");
            assertThat(report.imported().get(1).valued().major()).isEqualTo(85_000);
        }

        @Test
        void refusesASecondNoticeAgainstTheSameApplication() {
            String text = String.join(
                    "\n", "application,served on,valued", "SA-01,2025-02-27,38000.00", "SA-01,2025-02-28,37000.00");
            ImportReport<PaymentNotice> report = PaymentImport.importNotices(text, GBP);
            assertThat(report.rowsAccepted()).isEqualTo(1);
            assertThat(report.errors().get(0).message()).contains("two notices");
        }

        @Test
        void rejectsARowWithNoApplicationNamed() {
            assertThat(PaymentImport.importNotices("application,served on,valued\n,2025-02-27,38000.00", GBP)
                            .rowsAccepted())
                    .isZero();
        }
    }

    @Nested
    class ImportPayLessNotices {

        private final String csv = String.join(
                "\n",
                "application,served on,amount,reason",
                "SA-02,2025-05-30,20000.00,capping laid outside tolerance at 12+500");

        @Test
        void carriesTheReasonTheNoticeHasToGive() {
            ImportReport<PayLessNotice> report = PaymentImport.importPayLessNotices(csv, GBP);
            assertThat(report.rowsAccepted()).isEqualTo(1);
            assertThat(report.imported().get(0).reason()).contains("outside tolerance");
            assertThat(report.imported().get(0).amount().major()).isEqualTo(20_000);
        }

        @Test
        void rejectsANoticeWithNoReasonGiven() {
            String text = String.join("\n", "application,served on,amount,reason", "SA-02,2025-05-30,20000.00,");
            assertThat(PaymentImport.importPayLessNotices(text, GBP).rowsAccepted())
                    .isZero();
        }

        @Test
        void refusesTwoPayLessNoticesAgainstOneApplication() {
            String text = String.join(
                    "\n",
                    "application,served on,amount,reason",
                    "SA-02,2025-05-30,20000.00,first",
                    "SA-02,2025-05-31,19000.00,second");
            assertThat(PaymentImport.importPayLessNotices(text, GBP)
                            .errors()
                            .get(0)
                            .message())
                    .contains("two pay less notices");
        }
    }
}
