package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.itp.Ncr;
import io.github.rkaushikdev.chainageworks.domain.itp.NcrStatus;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOff;
import io.github.rkaushikdev.chainageworks.domain.itp.SignOffOutcome;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InspectionImportTest {

    private static final String SIGN_HEADER = String.join(",", InspectionImport.SIGN_OFF_COLUMNS);
    private static final String NCR_HEADER = String.join(",", InspectionImport.NCR_COLUMNS);

    private static String sheet(String header, String... rows) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.addAll(List.of(rows));
        return String.join("\n", lines);
    }

    @Nested
    class ImportSignOffs {

        @Test
        void readsACleanRow() {
            ImportReport<SignOff> report = InspectionImport.importSignOffs(
                    sheet(SIGN_HEADER, "SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer"));
            assertThat(report.hasErrors()).isFalse();
            SignOff signOff = report.imported().get(0);
            assertThat(signOff.id()).isEqualTo("SO-01");
            assertThat(signOff.pointId()).isEqualTo("IP-E1");
            assertThat(signOff.extent().format()).isEqualTo("12+000.000 to 12+400.000");
            assertThat(SiteDates.format(signOff.signedOn())).isEqualTo("2025-03-12");
            assertThat(signOff.outcome()).isEqualTo(SignOffOutcome.PASSED);
            assertThat(signOff.note()).isEmpty();
        }

        @Test
        void readsTheOptionalOutcomeAndNote() {
            ImportReport<SignOff> report = InspectionImport.importSignOffs(sheet(
                    SIGN_HEADER + ",outcome,note",
                    "SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer,passed with comment,minor step"));
            assertThat(report.imported().get(0).outcome()).isEqualTo(SignOffOutcome.PASSED_WITH_COMMENT);
            assertThat(report.imported().get(0).note()).isEqualTo("minor step");
        }

        @Test
        void rejectsAnOutcomeItDoesNotKnow() {
            ImportReport<SignOff> report = InspectionImport.importSignOffs(
                    sheet(SIGN_HEADER + ",outcome", "SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer,maybe"));
            assertThat(report.errors().get(0).message()).contains("is not one of passed");
            assertThat(report.errors().get(0).column()).contains("outcome");
        }

        @Test
        void rejectsARowWithNobodyNamed() {
            ImportReport<SignOff> report =
                    InspectionImport.importSignOffs(sheet(SIGN_HEADER, "SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,"));
            assertThat(report.errors().get(0).message()).isEqualTo("line 2: by is missing");
        }

        @Test
        void rejectsTheSecondRowThatReusesAnId() {
            String row = "SO-01,IP-E1,WP-01,12+000,12+400,2025-03-12,R Engineer";
            ImportReport<SignOff> report = InspectionImport.importSignOffs(sheet(SIGN_HEADER, row, row));
            assertThat(report.imported()).hasSize(1);
            assertThat(report.errors().get(0).message()).isEqualTo("sign off SO-01 appears twice");
        }

        @Test
        void namesEveryMissingColumnOnce() {
            assertThat(InspectionImport.importSignOffs("sign off,point\nSO-01,IP-E1")
                            .issues())
                    .hasSize(5);
        }
    }

    @Nested
    class ImportNcrs {

        @Test
        void readsACleanRowAndDefaultsTheRest() {
            ImportReport<Ncr> report = InspectionImport.importNcrs(
                    sheet(NCR_HEADER, "NCR-01,WP-01,12+100,12+150,2025-03-25,layer too thick"));
            assertThat(report.hasErrors()).isFalse();
            Ncr ncr = report.imported().get(0);
            assertThat(ncr.id()).isEqualTo("NCR-01");
            assertThat(ncr.status()).isEqualTo(NcrStatus.OPEN);
            assertThat(ncr.settledOn()).isEmpty();
            assertThat(ncr.stepId()).isEmpty();
            assertThat(ncr.withholdsCredit()).isTrue();
            assertThat(ncr.description()).isEqualTo("layer too thick");
        }

        @Test
        void readsTheStepTheStatusAndTheSettledDate() {
            ImportReport<Ncr> report = InspectionImport.importNcrs(sheet(
                    NCR_HEADER + ",step,status,settled on",
                    "NCR-02,WP-01,12+000,12+200,2025-02-10,density short,place,closed,2025-03-14"));
            Ncr ncr = report.imported().get(0);
            assertThat(ncr.stepId()).contains("place");
            assertThat(ncr.status()).isEqualTo(NcrStatus.CLOSED);
            assertThat(SiteDates.format(ncr.settledOn().orElseThrow())).isEqualTo("2025-03-14");
        }

        @Test
        void readsTheWithholdingFlagInEitherSpelling() {
            Map<String, Boolean> spellings =
                    Map.of("yes", true, "Y", true, "true", true, "no", false, "N", false, "0", false);
            spellings.forEach((text, expected) -> {
                ImportReport<Ncr> report = InspectionImport.importNcrs(
                        sheet(NCR_HEADER + ",withholds credit", "NCR-01,WP-01,12+100,12+150,2025-03-25,note," + text));
                assertThat(report.imported().get(0).withholdsCredit()).isEqualTo(expected);
            });
        }

        @Test
        void rejectsAWithholdingFlagItCannotRead() {
            ImportReport<Ncr> report = InspectionImport.importNcrs(
                    sheet(NCR_HEADER + ",withholds credit", "NCR-01,WP-01,12+100,12+150,2025-03-25,note,perhaps"));
            assertThat(report.errors().get(0).message()).isEqualTo("perhaps is not a yes or a no");
        }

        @Test
        void rejectsAClosedReportWithNoSettledDate() {
            ImportReport<Ncr> report = InspectionImport.importNcrs(
                    sheet(NCR_HEADER + ",status", "NCR-01,WP-01,12+100,12+150,2025-03-25,note,closed"));
            assertThat(report.errors().get(0).message()).contains("needs the date it was settled");
        }

        @Test
        void rejectsAReportSettledBeforeItWasRaised() {
            ImportReport<Ncr> report = InspectionImport.importNcrs(sheet(
                    NCR_HEADER + ",status,settled on", "NCR-01,WP-01,12+100,12+150,2025-03-25,note,closed,2025-01-01"));
            assertThat(report.errors().get(0).message()).contains("settled before it was raised");
        }

        @Test
        void rejectsTheSecondRowThatReusesAnId() {
            String row = "NCR-01,WP-01,12+100,12+150,2025-03-25,layer too thick";
            ImportReport<Ncr> report = InspectionImport.importNcrs(sheet(NCR_HEADER, row, row));
            assertThat(report.imported()).hasSize(1);
            assertThat(report.errors().get(0).message()).isEqualTo("report NCR-01 appears twice");
        }

        @Test
        void keepsReadingPastABadRow() {
            ImportReport<Ncr> report = InspectionImport.importNcrs(sheet(
                    NCR_HEADER,
                    "NCR-01,WP-01,12+100,12+150,2025-03-25,first",
                    "NCR-02,WP-01,nonsense,12+150,2025-03-25,second",
                    "NCR-03,WP-01,12+200,12+250,2025-03-26,third"));
            assertThat(report.imported().stream().map(Ncr::id)).containsExactly("NCR-01", "NCR-03");
            assertThat(report.errors()).hasSize(1);
        }
    }
}
