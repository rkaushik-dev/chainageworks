package io.github.rkaushikdev.chainageworks.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulAllocation;
import io.github.rkaushikdev.chainageworks.domain.earthworks.HaulSite;
import io.github.rkaushikdev.chainageworks.domain.earthworks.SiteKind;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.JobFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class HaulReportTest {

    private static final Job JOB = JobFixtures.haulingJob();
    private static final List<HaulSite> SITES = JOB.haulSites().all();
    private static final HaulAllocation ALLOCATION = HaulAllocation.allocate(new HaulAllocation.Input(
            SITES.stream()
                    .filter(s -> s.kind() == SiteKind.CUT || s.kind() == SiteKind.BORROW)
                    .toList(),
            SITES.stream()
                    .filter(s -> s.kind() == SiteKind.FILL || s.kind() == SiteKind.TIP)
                    .toList(),
            JOB.settings().haulLinks(),
            JOB.settings().haulRate()));

    @Test
    void rendersThePlanWithTheDearestMovementFirst() {
        assertThat(HaulReport.render(ALLOCATION, SITES))
                .isEqualTo(String.join(
                        "\n",
                        "haul plan",
                        "=========",
                        "",
                        "movements           2",
                        "haul moment         20000 m3 km",
                        "cost                £23,000.00",
                        "balanced            no",
                        "unplaced            3000.00 m3",
                        "unfilled            0.00 m3",
                        "spare tip capacity  5000.00 m3",
                        "",
                        "from                        to                              volume  km        haul"
                                + "       site        cost",
                        "--------------------------  --------------------------  ----------  --  ----------"
                                + "  ---------  ----------",
                        "CUT-A cutting at 12+400     EMB-B embankment at 13+200  6000.00 m3   3  £18,000.00"
                                + "      £0.00  £18,000.00",
                        "BOR-1 borrow pit off the …  EMB-B embankment at 13+200  1000.00 m3   2   £2,000.00"
                                + "  £3,000.00   £5,000.00"));
    }

    @Test
    void schedulesWhatHasToLeaveEachSource() {
        assertThat(HaulReport.muckAwaySchedule(ALLOCATION, SITES))
                .containsExactly(
                        "BOR-1 borrow pit off the haul road, 1000 m3 to EMB-B",
                        "CUT-A cutting at 12+400, 6000 m3 to EMB-B");
    }

    @Test
    void saysWhenASourceHasNowhereToGo() {
        HaulAllocation nothing = HaulAllocation.empty(JOB.settings().currency());
        assertThat(HaulReport.muckAwaySchedule(nothing, SITES))
                .containsExactly(
                        "BOR-1 borrow pit off the haul road, nowhere to go", "CUT-A cutting at 12+400, nowhere to go");
        assertThat(HaulReport.render(nothing, SITES)).doesNotContain("from");
    }
}
