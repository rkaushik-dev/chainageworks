package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.progress.Lookahead;
import io.github.rkaushikdev.chainageworks.progress.LookaheadInput;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class LookaheadReportTest {

    private static final Job JOB = JobFixtures.commercialJob();
    private static final Lookahead LOOKAHEAD = Lookahead.build(LookaheadInput.of(
            JOB.context(), JOB.packages().all(), JOB.calendar(), JOB.settings().outputAllowances(), d("2025-03-31")));

    @Test
    void rendersTheReadyWorkBeforeTheWorkThatIsWaiting() {
        assertThat(LookaheadReport.render(LOOKAHEAD))
                .isEqualTo(String.join(
                        "\n",
                        "lookahead 2025-03-31 to 2025-04-27",
                        "==================================",
                        "",
                        "working days in the window  20",
                        "days of work in front       20.4",
                        "fits the window             no",
                        "ready to start              2",
                        "waiting on an inspection    3",
                        "no allowance set            8",
                        "",
                        "ready to start",
                        "--------------",
                        "package  step      where                     metres  days  state",
                        "-------  --------  ------------------------  ------  ----  -----",
                        "WP-01    excavate  12+600.000 to 12+800.000     200   2.1  ready",
                        "WP-02    strip     12+800.000 to 13+600.000     800  10.0  ready",
                        "",
                        "waiting",
                        "-------",
                        "package  step      where                     metres  days  state",
                        "-------  --------  ------------------------  ------  ----  ----------------------------",
                        "WP-01    place     12+400.000 to 12+800.000     400        after excavate",
                        "WP-01    trim      12+000.000 to 12+800.000     800        after excavate, place",
                        "WP-02    excavate  12+800.000 to 13+600.000     800   8.3  held by IP-E1",
                        "WP-02    place     12+800.000 to 13+600.000     800        held by IP-E2",
                        "WP-02    trim      12+800.000 to 13+600.000     800        after strip, excavate, place",
                        "WP-03    subbase   12+000.000 to 13+600.000    1600        held by IP-P1",
                        "WP-03    base      12+000.000 to 13+600.000    1600        after subbase",
                        "WP-03    binder    12+000.000 to 13+600.000    1600        after subbase, base",
                        "WP-03    surface   12+000.000 to 13+600.000    1600        after subbase, base, binder"));
    }

    @Test
    void namesTheInspectionsToChase() {
        assertThat(LookaheadReport.chaseInspections(LOOKAHEAD))
                .containsExactly(
                        "WP-02 excavate at 12+800.000 to 13+600.000, held by IP-E1",
                        "WP-02 place at 12+800.000 to 13+600.000, held by IP-E2",
                        "WP-03 subbase at 12+000.000 to 13+600.000, held by IP-P1");
    }

    @Test
    void saysSoWhenNothingIsLeftToBuild() {
        Lookahead done = new Lookahead(d("2025-03-31"), d("2025-04-27"), 20, List.of(), 0);
        String text = LookaheadReport.render(done);
        assertThat(text).endsWith("\n\nnothing left to build");
        assertThat(text).contains("fits the window             yes").doesNotContain("waiting\n");
    }
}
