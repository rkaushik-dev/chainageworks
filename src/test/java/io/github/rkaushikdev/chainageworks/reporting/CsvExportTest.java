package io.github.rkaushikdev.chainageworks.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.app.Service;
import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import io.github.rkaushikdev.chainageworks.valuation.Certificate;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Every export pinned for the commercial job at valuation 3, cut off on 2025-03-25. */
class CsvExportTest {

    private static final Job JOB = JobFixtures.commercialJob();
    private static final Service SERVICE = new Service(JOB, Config.DEFAULT);
    private static final Certificate CERTIFICATE = SERVICE.certificateFor(3);
    private static final Outturn OUTTURN = SERVICE.outturnFor(3);
    private static final LocalDate CUT_OFF = CERTIFICATE.period().cutOff();

    /** The lines of a file, each ended by a newline. */
    private static String file(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    void writesTheBillInForceWithNumbersASpreadsheetCanAddUp() {
        assertThat(CsvExport.bill(JOB.context(), CUT_OFF))
                .isEqualTo(file(
                        "code,description,unit,quantity,rate,kind,value",
                        "A213.1,establish and remove site accommodation,sum,1,48500,lump,48500.00",
                        "E423.1,\"excavation for cuttings, maximum depth 2 to 5 m\",m3,12400,4.85,measured,60140.00",
                        "E423.2,\"excavation for cuttings, maximum depth 5 to 10 m\",m3,3200,5.4,measured,17280.00",
                        "E423.9,\"rock excavation, provisional\",m3,500,28,provisional,14000.00",
                        "E532.1,\"filling to embankments, selected excavated material\",m3,9800,3.15,measured,30870.00",
                        "F624.1,\"formwork, plane vertical, width over 1.22 m\",m2,640,32.5,measured,20800.00",
                        "R712.1,\"sub base, depth 225 mm\",m2,4000,11.4,measured,45600.00",
                        "R712.2,\"base, dense bitumen macadam, depth 120 mm\",m2,4000,18.75,measured,75000.00"));
    }

    @Test
    void writesTheSurveyBookAtTheDateWithTheExtentSplitInTwo() {
        assertThat(CsvExport.records(JOB.context(), CUT_OFF))
                .isEqualTo(file(
                        "record,package,code,step,from,to,quantity,unit,measured on,booked by,method",
                        "SR-01,WP-01,E423.1,strip,12+000.000,12+800.000,640,m3,2025-03-03,J Foreman,tape",
                        "SR-02,WP-01,E423.1,excavate,12+000.000,12+600.000,5200,m3,2025-03-10,J Foreman,tape",
                        "SR-03,WP-01,E532.1,place,12+000.000,12+400.000,2400,m3,2025-03-20,J Foreman,tape"));
    }

    @Test
    void writesTheCertificateOneRowPerAllocation() {
        assertThat(CsvExport.certificate(CERTIFICATE))
                .isEqualTo(file(
                        "valuation,cut off,package,percent,code,allocated,measured,earned,value,capped",
                        "3,2025-03-25,WP-01,32.5,E423.1,7000,5840,2275,11033.75,no",
                        "3,2025-03-25,WP-01,32.5,E532.1,5000,2400,1625,5118.75,no",
                        "3,2025-03-25,WP-03,0,R712.1,4000,0,0,0.00,no",
                        "3,2025-03-25,WP-03,0,R712.2,4000,0,0,0.00,no",
                        "3,2025-03-25,WP-02,0,E423.1,5000,0,0,0.00,no",
                        "3,2025-03-25,WP-02,0,E423.2,3200,0,0,0.00,no",
                        "3,2025-03-25,WP-02,0,E532.1,4800,0,0,0.00,no"));
    }

    @Test
    void writesTheCertificateLinesInTheOrderTheyPrint() {
        assertThat(CsvExport.certificateTotals(CERTIFICATE))
                .isEqualTo(file(
                        "item,amount",
                        "measured work,16152.50",
                        "materials on site,0.00",
                        "daywork,0.00",
                        "gross valuation,16152.50",
                        "less retention,807.63",
                        "net valuation,15344.87",
                        "less previously certified,0.00",
                        "amount due this certificate,15344.87"));
    }

    @Test
    void writesTheFinalAccountOneRowPerBillItem() {
        assertThat(CsvExport.outturn(OUTTURN))
                .isEqualTo(file(
                        "code,description,billed,measured,forecast,movement,percent,basis",
                        "A213.1,establish and remove site accommodation,48500.00,0.00,48500.00,0.00,0,bill",
                        "E423.1,\"excavation for cuttings, maximum depth 2 to 5 m\",60140.00,28324.00,60140.00,0.00,18.96,bill",
                        "E423.2,\"excavation for cuttings, maximum depth 5 to 10 m\",17280.00,0.00,17280.00,0.00,0,bill",
                        "E423.9,\"rock excavation, provisional\",14000.00,0.00,14000.00,0.00,0,bill",
                        "E532.1,\"filling to embankments, selected excavated material\",30870.00,7560.00,30870.00,0.00,16.58,bill",
                        "F624.1,\"formwork, plane vertical, width over 1.22 m\",20800.00,0.00,20800.00,0.00,0,bill",
                        "R712.1,\"sub base, depth 225 mm\",45600.00,0.00,45600.00,0.00,0,bill",
                        "R712.2,\"base, dense bitumen macadam, depth 120 mm\",75000.00,0.00,75000.00,0.00,0,bill"));
    }

    @Test
    void givesTheOutturnTotalInMajorUnits() {
        // The contract sum and the two variations in force: 312,190 + 2,425 + 63,130.
        assertThat(CsvExport.outturnTotalMajor(OUTTURN)).isEqualTo(377_745);
    }
}
