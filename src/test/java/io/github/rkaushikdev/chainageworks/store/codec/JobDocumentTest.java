package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.emptyJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.haulingJob;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The job file's format: the keys, their order and the text of the numbers are fixed, so a file
 * written before loads and writes back as the same bytes.
 */
class JobDocumentTest {

    /** A small job file with fractions in every place a fraction can sit. */
    private static final String FILE = "{\"version\":1,\"settings\":{\"id\":\"J9\",\"name\":\"format check\","
            + "\"currency\":\"GBP\",\"contractSum\":{\"minor\":100000,\"currency\":\"GBP\"},"
            + "\"retention\":{\"rate\":500,\"limit\":300,\"releaseOnCompletion\":5000},\"retentionBond\":null,"
            + "\"materialsAllowance\":8500,\"daywork\":{\"currency\":\"GBP\",\"labour\":[{\"id\":\"L1\","
            + "\"description\":\"ganger\",\"rate\":{\"milliMinor\":2850000,\"currency\":\"GBP\",\"unit\":\"hr\"}}],"
            + "\"plant\":[],\"additions\":{\"labour\":1200,\"plant\":800,\"materials\":1000},\"overtimeFactor\":1.5},"
            + "\"completion\":null,\"defectsPeriodEnds\":\"2025-06-30\","
            + "\"outputAllowances\":[{\"stepId\":\"strip\",\"metresPerHour\":12.5}],\"adjustment\":null,"
            + "\"contraNotifyDays\":14,\"riskMeetingDays\":14,\"haulRate\":{\"minor\":0,\"currency\":\"GBP\"},"
            + "\"haulLinks\":[{\"fromId\":\"CUT-A\",\"toId\":\"TIP-1\",\"km\":2.5}],"
            + "\"tolerances\":[{\"workClass\":null,\"allowance\":250},{\"workClass\":\"E\",\"allowance\":1000}],"
            + "\"workingDays\":[1,2,3,4,5],\"cutOffDay\":25,\"issueDays\":7,\"paymentDays\":14,"
            + "\"substantialCompletionOn\":null,\"defectsCertificateOn\":null},"
            + "\"alignments\":[],\"billRevisions\":[],\"packages\":[],\"profiles\":[],\"plans\":[],\"signOffs\":[],"
            + "\"ncrs\":[],\"records\":[],\"materials\":[],\"variations\":[],"
            + "\"dayworkSheets\":[{\"id\":\"DW-01\",\"reference\":\"\",\"packageId\":\"WP-01\",\"instruction\":\"\","
            + "\"workedOn\":\"2025-03-04\",\"signedOn\":null,\"signedBy\":\"\",\"labour\":[{\"rateId\":\"L1\","
            + "\"people\":2,\"hours\":3.5,\"overtimeHours\":0}],\"plant\":[],\"materials\":[]}],"
            + "\"delayEvents\":[],\"defects\":[],\"subcontracts\":[],\"paymentApplications\":[],"
            + "\"paymentNotices\":[],\"payLessNotices\":[],\"resourceReturns\":[{\"id\":\"RR-01\","
            + "\"packageId\":\"WP-01\",\"stepId\":\"strip\",\"date\":\"2025-03-03\",\"shift\":\"day\","
            + "\"labour\":[{\"resourceId\":\"GANG\",\"description\":\"earthworks gang\",\"count\":4,\"hours\":7.5,"
            + "\"kind\":\"working\"}],\"plant\":[],\"returnedBy\":\"A Kerrigan\",\"note\":\"\"}],"
            + "\"indices\":[],\"contraCharges\":[],\"haulSites\":[],\"earlyWarnings\":[],"
            + "\"holidays\":[\"2025-04-18\"],"
            + "\"standdowns\":[{\"date\":\"2025-03-05\",\"reason\":\"weather\",\"note\":\"rain\"}]}";

    @Test
    void keepsTheDocumentKeysInTheirFileOrder() {
        ObjectNode doc = JobCodec.toJson(haulingJob());
        List<String> keys = new ArrayList<>();
        doc.fieldNames().forEachRemaining(keys::add);
        assertThat(keys)
                .containsExactly(
                        "version",
                        "settings",
                        "alignments",
                        "billRevisions",
                        "packages",
                        "profiles",
                        "plans",
                        "signOffs",
                        "ncrs",
                        "records",
                        "materials",
                        "variations",
                        "dayworkSheets",
                        "delayEvents",
                        "defects",
                        "subcontracts",
                        "paymentApplications",
                        "paymentNotices",
                        "payLessNotices",
                        "resourceReturns",
                        "indices",
                        "contraCharges",
                        "haulSites",
                        "earlyWarnings",
                        "holidays",
                        "standdowns");
    }

    @Test
    void writesTheSettingsOfADefaultJobAsTheFileHasAlwaysHeldThem() {
        assertThat(Json.stringify(JobCodec.toJson(emptyJob()).get("settings")))
                .isEqualTo("{\"id\":\"A1-IMP\",\"name\":\"A1 improvement, section 2\",\"currency\":\"GBP\","
                        + "\"contractSum\":{\"minor\":31219000,\"currency\":\"GBP\"},"
                        + "\"retention\":{\"rate\":500,\"limit\":300,\"releaseOnCompletion\":5000},"
                        + "\"retentionBond\":null,\"materialsAllowance\":null,\"daywork\":null,\"completion\":null,"
                        + "\"defectsPeriodEnds\":null,\"outputAllowances\":[],\"adjustment\":null,"
                        + "\"contraNotifyDays\":14,\"riskMeetingDays\":14,\"haulRate\":{\"minor\":0,\"currency\":\"GBP\"},"
                        + "\"haulLinks\":[],\"tolerances\":[{\"workClass\":null,\"allowance\":250},"
                        + "{\"workClass\":\"E\",\"allowance\":1000},{\"workClass\":\"D\",\"allowance\":1500},"
                        + "{\"workClass\":\"K\",\"allowance\":0},{\"workClass\":\"I\",\"allowance\":500}],"
                        + "\"workingDays\":[1,2,3,4,5],\"cutOffDay\":25,\"issueDays\":7,\"paymentDays\":14,"
                        + "\"substantialCompletionOn\":null,\"defectsCertificateOn\":null}");
    }

    @Test
    void readsAFileWithFractionsAndWritesItBackUnchanged() throws Json.JsonFailure {
        Job job = JobCodec.fromJson(Json.parse(FILE));
        assertThat(job.settings().haulLinks().get(0).km()).isEqualTo(2.5);
        assertThat(job.resourceReturns().require("RR-01").labour().get(0).hours())
                .isEqualTo(7.5);
        assertThat(Json.stringify(JobCodec.toJson(job))).isEqualTo(FILE);
    }
}
