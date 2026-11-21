package io.github.rkaushikdev.chainageworks.app;

import static io.github.rkaushikdev.chainageworks.support.JobFixtures.commercialJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.emptyJob;
import static io.github.rkaushikdev.chainageworks.support.JobFixtures.sampleJob;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.config.Config;
import io.github.rkaushikdev.chainageworks.config.ConfigOverrides;
import io.github.rkaushikdev.chainageworks.store.Job;
import io.github.rkaushikdev.chainageworks.support.ContextFixtures;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StartupTest {

    private static final Config CONFIG = Config.DEFAULT;

    @Nested
    class StartupReportOnAJobThatCanBeValued {

        private final Startup startup = Startup.report(new Service(sampleJob(), CONFIG), CONFIG);

        @Test
        void namesTheFileTheJobAndWhereItIsListening() {
            assertThat(startup.lines().get(0)).isEqualTo("chainageworks serving chainageworks.json");
            assertThat(startup.lines().get(1)).isEqualTo("job          A1-IMP A1 improvement, section 2");
            assertThat(startup.lines()).contains("contract sum £312,190.00");
            assertThat(startup.lines()).contains("listening    127.0.0.1:8730");
        }

        @Test
        void countsWhatTheJobIsCarrying() {
            assertThat(startup.lines()).contains("packages     3");
            assertThat(startup.lines()).contains("records      3");
        }

        @Test
        void takesTheHostAndThePortOffTheConfigItWasGiven() {
            Config other =
                    CONFIG.merge(ConfigOverrides.none().withHost("0.0.0.0").withPort(9100));
            List<String> lines =
                    Startup.report(new Service(sampleJob(), other), other).lines();
            assertThat(lines).contains("listening    0.0.0.0:9100");
        }

        @Test
        void serves() {
            assertThat(startup.refuses()).isFalse();
            assertThat(startup.summary()).isEqualTo("ready");
        }

        @Test
        void mentionsTheWarningsWithoutRefusingOverThem() {
            assertThat(startup.lines()).anyMatch(line -> line.startsWith("warnings"));
            assertThat(startup.lines()).noneMatch(line -> line.startsWith("errors"));
        }
    }

    @Nested
    class StartupReportOnAJobWithNothingLoaded {

        private final Startup startup = Startup.report(new Service(emptyJob(), CONFIG), CONFIG);

        @Test
        void namesTheBlockers() {
            assertThat(startup.lines()).anyMatch(line -> line.startsWith("blockers"));
            assertThat(String.join("\n", startup.lines())).contains("no bill has been loaded");
        }

        @Test
        void refusesToServe() {
            assertThat(startup.refuses()).isTrue();
            assertThat(startup.summary()).isEqualTo("refusing to serve, the job cannot be valued");
        }
    }

    @Nested
    class StartupReportOnAJobWithADanglingReference {

        private final Service service = new Service(jobWithADanglingSignOff(), CONFIG);
        private final Startup startup = Startup.report(service, CONFIG);

        private static Job jobWithADanglingSignOff() {
            Job job = commercialJob();
            job.signOffs()
                    .add(ContextFixtures.signOff("SO-99", "IP-NOWHERE", "WP-01", "12+000", "12+400", "2025-03-12"));
            return job;
        }

        @Test
        void saysHowManyErrorsThereAreAndPointsAtTheDoctor() {
            assertThat(String.join("\n", startup.lines())).contains("errors       1, run cw doctor");
        }

        @Test
        void servesAnywayBecauseMostOfTheNumbersAreStillRight() {
            assertThat(startup.refuses()).isFalse();
            assertThat(startup.summary()).isEqualTo("ready");
        }

        @Test
        void isNotSoundWhichIsADifferentQuestionFromWhetherItServes() {
            assertThat(Startup.isSound(service)).isFalse();
        }
    }

    @Nested
    class IsSound {

        @Test
        void isTrueForAJobTheToolBuiltItself() {
            assertThat(Startup.isSound(new Service(commercialJob(), CONFIG))).isTrue();
        }

        @Test
        void isFalseForAJobWithNothingLoaded() {
            assertThat(Startup.isSound(new Service(emptyJob(), CONFIG))).isFalse();
        }
    }
}
