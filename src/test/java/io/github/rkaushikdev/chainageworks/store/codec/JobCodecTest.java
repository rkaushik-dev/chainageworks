package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.defects.Defect;
import io.github.rkaushikdev.chainageworks.domain.defects.DefectInput;
import io.github.rkaushikdev.chainageworks.domain.itp.PointType;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet;
import io.github.rkaushikdev.chainageworks.domain.time.Standdown;
import io.github.rkaushikdev.chainageworks.domain.time.StanddownReason;
import io.github.rkaushikdev.chainageworks.domain.variation.ValuationBasis;
import io.github.rkaushikdev.chainageworks.domain.variation.Variation;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationInput;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationItem;
import io.github.rkaushikdev.chainageworks.domain.variation.VariationStatus;
import io.github.rkaushikdev.chainageworks.progress.PackageEarning;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import io.github.rkaushikdev.chainageworks.valuation.AdvancePayment;
import io.github.rkaushikdev.chainageworks.valuation.InterimTerms;
import io.github.rkaushikdev.chainageworks.valuation.MaterialsInput;
import io.github.rkaushikdev.chainageworks.valuation.MaterialsOnSite;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JobCodecTest {

    /** The job written to text and read back, as a save and a load do. */
    static Job through(Job job) {
        try {
            return JobCodec.fromJson(Json.parse(Json.stringify(JobCodec.toJson(job))));
        } catch (Json.JsonFailure broken) {
            throw new AssertionError(broken);
        }
    }

    private static Job through() {
        return through(sampleJob());
    }

    private static Money gbp(double major) {
        return Money.fromMajor(major, Currency.GBP);
    }

    @Nested
    class JobToJson {

        private final ObjectNode doc = JobCodec.toJson(sampleJob());

        @Test
        void stampsTheDocumentVersion() {
            assertThat(doc.get("version").intValue()).isEqualTo(JobCodec.DOCUMENT_VERSION);
        }

        @Test
        void writesEveryRegister() {
            assertThat(doc.get("alignments")).hasSize(1);
            assertThat(doc.get("billRevisions")).hasSize(1);
            assertThat(doc.get("packages")).hasSize(3);
            assertThat(doc.get("profiles")).hasSize(7);
            assertThat(doc.get("plans")).hasSize(2);
            assertThat(doc.get("signOffs")).hasSize(2);
            assertThat(doc.get("ncrs")).hasSize(1);
            assertThat(doc.get("records")).hasSize(3);
        }

        @Test
        void survivesJsonStringify() {
            assertThatCode(() -> Json.stringify(doc)).doesNotThrowAnyException();
        }
    }

    @Nested
    class JobFromJson {

        @Test
        void bringsBackTheSettings() {
            Job job = through();
            assertThat(job.settings().id()).isEqualTo("A1-IMP");
            assertThat(job.settings().contractSum().major()).isEqualTo(312_190);
            assertThat(job.settings().cutOffDay()).isEqualTo(25);
            assertThat(job.settings().workingDays()).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void bringsBackTheAlignmentWithItsLengthIntact() {
            assertThat(through().alignments().require("A1").length()).isEqualTo(2_000_000);
        }

        @Test
        void bringsBackTheBillExactly() {
            BillRevision revision = through().billRevisions().require("Rev A");
            assertThat(revision.bill().total().major()).isEqualTo(312_190);
            assertThat(revision.bill().items()).hasSize(8);
        }

        @Test
        void bringsBackThePackagesAndTheirAllocations() {
            Job job = through();
            assertThat(job.packages().count()).isEqualTo(3);
            assertThat(job.packages().require("WP-01").allocations()).hasSize(2);
            assertThat(job.packages().require("WP-03").profileId()).isEqualTo("pavement");
        }

        @Test
        void bringsBackThePlansAgainstTheRulesOfCreditTheyGate() {
            Job job = through();
            assertThat(job.plans().require("ITP-EW").points()).hasSize(3);
            assertThat(job.plans().require("ITP-EW").points().get(0).type()).isEqualTo(PointType.HOLD);
        }

        @Test
        void bringsBackTheSiteRecordsSignOffsAndReports() {
            Job job = through();
            assertThat(job.records().require("SR-02").stepId()).isEqualTo("excavate");
            assertThat(job.signOffs().require("SO-01").by()).isEqualTo("R Engineer");
            assertThat(job.ncrs().require("NCR-01").withholdsCredit()).isTrue();
        }

        @Test
        void valuesTheSameAfterARoundTripWhichIsTheWholePoint() {
            List<PackageEarning> before = PackageEarning.earnAll(sampleJob().context(), d("2025-03-31"));
            List<PackageEarning> after = PackageEarning.earnAll(through().context(), d("2025-03-31"));
            assertThat(after.stream().map(PackageEarning::earnedValue).toList())
                    .isEqualTo(before.stream().map(PackageEarning::earnedValue).toList());
            assertThat(after.get(0).earnedValue().major()).isEqualTo(16_152.5);
        }

        @Test
        void carriesTheHolidaysAndTheStanddowns() {
            Job job = sampleJob();
            job.setHolidays(List.of(d("2025-04-18")));
            job.setStanddowns(List.of(new Standdown(d("2025-03-05"), StanddownReason.WEATHER, "rain")));
            Job again = through(job);
            assertThat(again.holidays()).containsExactly(d("2025-04-18"));
            assertThat(again.standdowns().get(0).reason()).isEqualTo(StanddownReason.WEATHER);
            assertThat(again.standdowns().get(0).note()).contains("rain");
        }

        @Test
        void carriesMaterialsAndVariations() {
            Job job = sampleJob();
            job.materials()
                    .add(MaterialsOnSite.build(MaterialsInput.of(
                                    "MOS-01",
                                    "WP-02",
                                    ItemCode.parse("E423.1"),
                                    "imported fill",
                                    Quantity.of(1200, Unit.M3),
                                    gbp(9000),
                                    d("2025-03-10"))
                            .withVestedOn(d("2025-03-12"))));
            job.materials()
                    .add(MaterialsOnSite.build(MaterialsInput.of(
                                    "MOS-02",
                                    "WP-02",
                                    ItemCode.parse("E423.1"),
                                    "geotextile rolls",
                                    Quantity.of(40, Unit.NR),
                                    gbp(2000),
                                    d("2025-03-10"))
                            .withVestedOn(d("2025-03-12"))
                            .withStoredOffSite("supplier yard, Thame", "BOND/OS/002")));
            job.variations()
                    .add(Variation.build(VariationInput.of(
                                    "VO-012",
                                    "EI-045",
                                    "deepen the cutting",
                                    List.of(VariationItem.priced(
                                            ItemCode.parse("X400.1"),
                                            Quantity.of(40, Unit.M3),
                                            Rate.of(94, Currency.GBP, Unit.M3),
                                            ValuationBasis.STAR_RATE)))
                            .withStatus(VariationStatus.INSTRUCTED)
                            .withInstructedOn(d("2025-03-14"))));
            Job again = through(job);
            assertThat(again.materials().require("MOS-01").description()).isEqualTo("imported fill");
            assertThat(again.materials().require("MOS-01").isOffSite()).isFalse();
            assertThat(again.materials().require("MOS-02").storedAt()).contains("supplier yard, Thame");
            assertThat(again.materials().require("MOS-02").bondReference()).contains("BOND/OS/002");
            assertThat(again.variations().require("VO-012").items().get(0).basis())
                    .isEqualTo(ValuationBasis.STAR_RATE);
        }

        @Test
        void keepsAJobWithDifferentSettings() {
            Job job = sampleJob();
            job.setSettings(job.settings()
                    .withMaterialsAllowance(Percent.of(85))
                    .withWorkingDays(List.of(1, 2, 3, 4, 5, 6))
                    .withSubstantialCompletionOn(d("2025-11-01")));
            Job again = through(job);
            assertThat(again.settings().materialsAllowance()).contains(Percent.of(85));
            assertThat(again.settings().workingDays()).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(again.settings().substantialCompletionOn()).contains(d("2025-11-01"));
        }

        @Test
        void keepsTheMinimumCertificateAndLeavesItOutWhenThereIsNone() {
            Job job = sampleJob();
            job.setSettings(
                    job.settings().withInterimTerms(InterimTerms.withMinimum(Money.fromMajor(25_000, Currency.GBP))));
            assertThat(JobCodec.toJson(job)
                            .path("settings")
                            .path("minimumCertificate")
                            .isMissingNode())
                    .isFalse();
            assertThat(through(job).settings().interimTerms().minimumCertificate())
                    .contains(Money.fromMajor(25_000, Currency.GBP));
            assertThat(JobCodec.toJson(sampleJob()).path("settings").has("minimumCertificate"))
                    .isFalse();
            assertThat(through(sampleJob()).settings().interimTerms()).isEqualTo(InterimTerms.NONE);
        }

        @Test
        void keepsTheAdvancePaymentAndItsRepaymentTerms() {
            Job job = sampleJob();
            AdvancePayment advance = AdvancePayment.build(
                    Money.fromMajor(40_000, Currency.GBP), d("2025-01-20"), Percent.of(15), Percent.of(30));
            job.setSettings(job.settings().withInterimTerms(InterimTerms.NONE.withAdvance(advance)));
            Job again = through(job);
            assertThat(again.settings().interimTerms().advance()).contains(advance);
            assertThat(again.settings().interimTerms().minimumCertificate()).isEmpty();
            ObjectNode doc = JobCodec.toJson(job);
            ((ObjectNode) doc.path("settings").path("advancePayment")).remove("repaymentRate");
            assertThat(JobCodec.fromJson(doc)
                            .settings()
                            .interimTerms()
                            .advance()
                            .orElseThrow()
                            .repaymentRate())
                    .isEqualTo(AdvancePayment.DEFAULT_REPAYMENT_RATE);
        }

        @Test
        void keepsADefectThatShutTheWorks() {
            Job job = commercialJob();
            job.defects()
                    .add(Defect.build(DefectInput.of(
                                    "DF-90", "DEF/2025/090", "WP-03", "lane closed", d("2025-04-07"), d("2025-04-21"))
                            .withPreventsUse(true)));
            Job again = through(job);
            assertThat(again.defects().require("DF-90").preventsUse()).isTrue();
            assertThat(again.defects().all().stream().filter(Defect::preventsUse))
                    .hasSize(1);
        }

        @Test
        void keepsAToleranceRuleWrittenAgainstAHeading() {
            Job job = sampleJob();
            ToleranceSet rules = ToleranceSet.build(List.of(
                    ToleranceSet.Rule.fallback(Percent.of(2.5)),
                    ToleranceSet.Rule.forClass("E", Percent.of(10)),
                    ToleranceSet.Rule.forHeading(ItemCode.parse("E42"), Percent.of(20))));
            job.setSettings(job.settings().withTolerances(rules));
            JsonNode written = JobCodec.toJson(job).path("settings").path("tolerances");
            assertThat(written.get(1).path("workClass").asText()).isEqualTo("E");
            assertThat(written.get(2).path("heading").asText()).isEqualTo("E42");
            assertThat(through(job).settings().tolerances().allowanceFor(ItemCode.parse("E423.1")))
                    .isEqualTo(Percent.of(20));
        }

        @Test
        void refusesADocumentFromAnotherVersion() {
            ObjectNode doc = JobCodec.toJson(sampleJob());
            doc.put("version", 99);
            assertThatThrownBy(() -> JobCodec.fromJson(doc)).hasMessageContaining("written by a different version");
        }

        @Test
        void refusesSomethingThatIsNotADocumentAtAll() {
            assertThatThrownBy(() -> JobCodec.fromJson(TextNode.valueOf("nonsense")))
                    .hasMessageContaining("expected an object");
            assertThatThrownBy(() -> JobCodec.fromJson(NullNode.getInstance())).isInstanceOf(BadInputException.class);
        }

        @Test
        void refusesAPlanThatGatesARuleOfCreditTheJobDoesNotCarry() {
            ObjectNode doc = JobCodec.toJson(sampleJob());
            ArrayNode profiles = Json.array();
            for (JsonNode profile : doc.get("profiles")) {
                if (!profile.get("id").textValue().equals("bulk-earthworks")) {
                    profiles.add(profile);
                }
            }
            doc.set("profiles", profiles);
            assertThatThrownBy(() -> JobCodec.fromJson(doc)).hasMessageContaining("does not carry");
        }

        @Test
        void refusesARecordWhoseItemCodeIsNonsense() {
            ObjectNode doc = JobCodec.toJson(sampleJob());
            ((ObjectNode) doc.get("records").get(0)).put("code", "423");
            assertThatThrownBy(() -> JobCodec.fromJson(doc)).isInstanceOf(BadInputException.class);
        }
    }
}
