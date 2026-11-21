package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.JobCodecTest.through;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.fluctuatingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.haulingJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Every register of a job goes through the document, and a document short of one still loads. */
class JobCodecRegistersTest {

    /** One register the job document carries, and how to count it on a job. */
    private record Counted(String key, ToIntFunction<Job> count) {}

    /**
     * Every register the job document carries, and how to count it. The point of the list is that a
     * register added to the codec table and forgotten here shows up as a document key nothing counts.
     */
    private static final List<Counted> REGISTERS = List.of(
            new Counted("alignments", job -> job.alignments().count()),
            new Counted("billRevisions", job -> job.billRevisions().count()),
            new Counted("packages", job -> job.packages().count()),
            new Counted("profiles", job -> job.profiles().count()),
            new Counted("plans", job -> job.plans().count()),
            new Counted("signOffs", job -> job.signOffs().count()),
            new Counted("ncrs", job -> job.ncrs().count()),
            new Counted("records", job -> job.records().count()),
            new Counted("materials", job -> job.materials().count()),
            new Counted("variations", job -> job.variations().count()),
            new Counted("dayworkSheets", job -> job.dayworkSheets().count()),
            new Counted("delayEvents", job -> job.delayEvents().count()),
            new Counted("defects", job -> job.defects().count()),
            new Counted("subcontracts", job -> job.subcontracts().count()),
            new Counted("paymentApplications", job -> job.paymentApplications().count()),
            new Counted("paymentNotices", job -> job.paymentNotices().count()),
            new Counted("payLessNotices", job -> job.payLessNotices().count()),
            new Counted("resourceReturns", job -> job.resourceReturns().count()),
            new Counted("indices", job -> job.indices().count()),
            new Counted("contraCharges", job -> job.contraCharges().count()),
            new Counted("haulSites", job -> job.haulSites().count()));

    @Nested
    class TheJobDocumentHoldsEveryRegister {

        @Test
        void writesAKeyForEachOne() {
            ObjectNode doc = JobCodec.toJson(haulingJob());
            for (Counted register : REGISTERS) {
                assertThat(doc.path(register.key()).isArray())
                        .as(register.key())
                        .isTrue();
            }
        }

        @Test
        void writesNothingUnderANameNoRegisterUses() {
            ObjectNode doc = JobCodec.toJson(haulingJob());
            assertThat(doc.has("insurances")).isFalse();
        }
    }

    @Nested
    class AFullJobThroughTheFile {

        private final Job job = haulingJob();
        private final Job again = through(job);

        @Test
        void bringsBackTheSameCountInEveryRegister() {
            for (Counted register : REGISTERS) {
                assertThat(register.count().applyAsInt(again))
                        .as(register.key())
                        .isEqualTo(register.count().applyAsInt(job));
            }
        }

        @Test
        void bringsBackARegisterThatWasCarryingNothing() {
            assertThat(again.materials().count()).isZero();
            assertThat(again.dayworkSheets().count()).isZero();
        }

        @Test
        void survivesASecondTripUnchanged() {
            assertThat(Json.stringify(JobCodec.toJson(through(again))))
                    .isEqualTo(Json.stringify(JobCodec.toJson(again)));
        }
    }

    @Nested
    class AJobWithAFluctuationClauseThroughTheFile {

        @Test
        void keepsTheClauseAndTheBulletins() {
            Job again = through(fluctuatingJob());
            assertThat(again.indices().count()).isEqualTo(2);
            assertThat(again.settings().adjustment()).isPresent();
        }
    }

    @Nested
    class ABareJobThroughTheFile {

        private final Job again = through(sampleJob());

        @Test
        void bringsBackTheLoadedRegistersAndLeavesTheRestEmpty() {
            assertThat(again.packages().count()).isEqualTo(3);
            assertThat(again.records().count()).isEqualTo(3);
            assertThat(again.subcontracts().count()).isZero();
            assertThat(again.haulSites().count()).isZero();
        }

        @Test
        void replacesTheSeededRulesOfCreditWithTheOnesOnTheFile() {
            Job job = sampleJob();
            assertThat(again.profiles().count()).isEqualTo(job.profiles().count());
        }

        @Test
        void readsThePlansAgainstTheProfilesThatCameOffTheFile() {
            assertThat(again.plans().count()).isEqualTo(2);
            assertThat(again.plans().require("ITP-EW").profileId()).isEqualTo("bulk-earthworks");
        }
    }

    @Nested
    class ADocumentMissingARegisterANewerVersionWrites {

        @Test
        void readsItAsEmptyRatherThanRefusingTheFile() {
            ObjectNode doc = JobCodec.toJson(commercialJob());
            doc.remove("contraCharges");
            doc.remove("haulSites");
            Job again = JobCodec.fromJson(doc);
            assertThat(again.contraCharges().count()).isZero();
            assertThat(again.haulSites().count()).isZero();
            assertThat(again.subcontracts().count()).isEqualTo(2);
        }

        @Test
        void stillRefusesADocumentWrittenByAnotherVersion() {
            ObjectNode doc = JobCodec.toJson(sampleJob());
            doc.put("version", 99);
            assertThatThrownBy(() -> JobCodec.fromJson(doc)).hasMessageContaining("different version");
        }
    }
}
