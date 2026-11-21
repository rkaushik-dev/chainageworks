package io.github.rkaushikdev.chainageworks.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import io.github.rkaushikdev.chainageworks.support.JobValuations;
import io.github.rkaushikdev.chainageworks.util.Text;
import io.github.rkaushikdev.chainageworks.valuation.Outturn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The final account page pinned for the commercial job at valuation 3, cut off on 2025-03-25. */
class OutturnReportTest {

    private static final Job JOB = JobFixtures.commercialJob();

    /** The same job with every item forecast on its trend from ten percent complete. */
    private static final Outturn TRENDING = Outturn.build(
            JobValuations.outturnInput(JOB, JobValuations.period(JOB, 3).cutOff())
                    .withTrendThreshold(Percent.of(10)));

    private static final List<String> HEAD = List.of(
            "anticipated final account at 2025-03-25",
            "=======================================",
            "",
            "item                     amount",
            "------------------  -----------",
            "measured works      £416,173.94",
            "variations           £65,555.00",
            "daywork                   £0.00",
            "price adjustment          £0.00",
            "liquidated damages        £0.00",
            "forecast outturn    £481,728.94",
            "",
            "contract sum      £312,190.00",
            "movement          £169,538.94",
            "movement percent  54.31%",
            "position          over the contract sum",
            "parts add up      yes",
            "price adjustment  firm",
            "",
            "items moving away from the bill",
            "-------------------------------",
            "code    description                             billed     forecast    movement  complete  basis",
            "------  ----------------------------------  ----------  -----------  ----------  --------  -----",
            "E423.1  excavation for cuttings, maximum …  £60,140.00  £149,401.32  £89,261.32    18.96%  trend");

    @Test
    void setsThePartsOutThenThePositionWhenNothingHasMoved() {
        assertThat(OutturnReport.render(JobValuations.outturn(JOB, 3)))
                .isEqualTo(String.join(
                        "\n",
                        "anticipated final account at 2025-03-25",
                        "=======================================",
                        "",
                        "item                     amount",
                        "------------------  -----------",
                        "measured works      £312,190.00",
                        "variations           £65,555.00",
                        "daywork                   £0.00",
                        "price adjustment          £0.00",
                        "liquidated damages        £0.00",
                        "forecast outturn    £377,745.00",
                        "",
                        "contract sum      £312,190.00",
                        "movement          £65,555.00",
                        "movement percent  21.00%",
                        "position          over the contract sum",
                        "parts add up      yes",
                        "price adjustment  firm"));
    }

    @Test
    void listsTheItemsMovingAwayFromTheBillLargestMovementFirst() {
        List<String> expected = new ArrayList<>(HEAD);
        expected.add(
                "E532.1  filling to embankments, selected …  £30,870.00   £45,592.62  £14,722.62    16.58%  trend");
        assertThat(OutturnReport.render(TRENDING)).isEqualTo(String.join("\n", expected));
    }

    @Test
    void listsOnlyTheWorstFewMovingItems() {
        assertThat(Text.split(OutturnReport.render(TRENDING, 1), "\n")).isEqualTo(HEAD);
    }
}
