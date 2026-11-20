package io.github.rkaushikdev.chainageworks.domain.itp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NcrTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static ChainageRange r(String from, String to) {
        return ChainageRange.of(Stations.parse(from), Stations.parse(to));
    }

    private static List<String> show(List<ChainageRange> ranges) {
        return ranges.stream().map(ChainageRange::format).toList();
    }

    private static final Ncr OPEN_NCR = Ncr.build(NcrInput.of(
                    "NCR-01",
                    "WP-01",
                    r("12+200", "12+400"),
                    d("2025-03-05"),
                    "fill placed at 350 mm layers against a 250 mm specification")
            .withStep("place"));

    private static final Ncr CLOSED_NCR = Ncr.build(NcrInput.of(
                    "NCR-02",
                    "WP-01",
                    r("12+000", "12+200"),
                    d("2025-02-10"),
                    "compaction short of the specified density, relaid")
            .withStep("place")
            .withSettlement(NcrStatus.CLOSED, d("2025-03-14")));

    private static final Ncr WHOLE_PACKAGE = Ncr.build(
            NcrInput.of("NCR-03", "WP-01", r("12+600", "12+800"), d("2025-03-01"), "no records kept for the bay"));

    private static final List<Ncr> NCRS = List.of(OPEN_NCR, CLOSED_NCR, WHOLE_PACKAGE);

    @Nested
    class Build {

        @Test
        void defaultsToOpenWithholdingCreditAndTheWholePackage() {
            assertThat(WHOLE_PACKAGE.status()).isEqualTo(NcrStatus.OPEN);
            assertThat(WHOLE_PACKAGE.withholdsCredit()).isTrue();
            assertThat(WHOLE_PACKAGE.stepId()).isEmpty();
            assertThat(WHOLE_PACKAGE.settledOn()).isEmpty();
        }

        @Test
        void trimsTheDescriptionAndRefusesAnEmptyOne() {
            assertThat(Ncr.build(NcrInput.from(WHOLE_PACKAGE).withDescription("  spalled edge  "))
                            .description())
                    .isEqualTo("spalled edge");
            assertThatThrownBy(() -> Ncr.build(NcrInput.from(WHOLE_PACKAGE).withDescription("   ")))
                    .hasMessageContaining("needs a description");
        }

        @Test
        void refusesAnOpenReportWithASettledDate() {
            assertThatThrownBy(() -> Ncr.build(NcrInput.from(WHOLE_PACKAGE).withSettledOn(d("2025-03-14"))))
                    .hasMessageContaining("cannot carry a settled date");
        }

        @Test
        void refusesASettledReportWithNoDate() {
            assertThatThrownBy(() -> Ncr.build(NcrInput.from(WHOLE_PACKAGE).withStatus(NcrStatus.CLOSED)))
                    .hasMessageContaining("needs the date it was settled");
        }

        @Test
        void refusesAReportSettledBeforeItWasRaised() {
            assertThatThrownBy(() ->
                            Ncr.build(NcrInput.from(WHOLE_PACKAGE).withSettlement(NcrStatus.CLOSED, d("2025-02-01"))))
                    .hasMessageContaining("settled before it was raised");
        }

        @Test
        void acceptsAConcessionAsASettledOutcome() {
            Ncr accepted = Ncr.build(NcrInput.from(WHOLE_PACKAGE).withSettlement(NcrStatus.ACCEPTED, d("2025-03-20")));
            assertThat(accepted.status()).isEqualTo(NcrStatus.ACCEPTED);
        }
    }

    @Nested
    class IsOpenAt {

        @Test
        void isClosedBeforeItWasRaised() {
            assertThat(OPEN_NCR.isOpenAt(d("2025-03-04"))).isFalse();
        }

        @Test
        void isOpenFromTheDayItWasRaised() {
            assertThat(OPEN_NCR.isOpenAt(d("2025-03-05"))).isTrue();
            assertThat(OPEN_NCR.isOpenAt(d("2025-12-31"))).isTrue();
        }

        @Test
        void stopsBeingOpenOnTheDayItWasSettled() {
            assertThat(CLOSED_NCR.isOpenAt(d("2025-03-13"))).isTrue();
            assertThat(CLOSED_NCR.isOpenAt(d("2025-03-14"))).isFalse();
        }
    }

    @Nested
    class SettledIn {

        @Test
        void findsReportsSettledInsideAWindow() {
            assertThat(Ncr.settledIn(NCRS, d("2025-03-01"), d("2025-03-31")).stream()
                            .map(Ncr::id))
                    .containsExactly("NCR-02");
            assertThat(Ncr.settledIn(NCRS, d("2025-04-01"), d("2025-04-30"))).isEmpty();
        }
    }

    @Nested
    class WithheldExtent {

        @Test
        void holdsBackTheStretchAnOpenReportCovers() {
            List<ChainageRange> held =
                    Ncr.withheldExtent(new Ncr.WithholdQuery(NCRS, "WP-01", "place", d("2025-03-10")));
            assertThat(show(held)).containsExactly("12+000.000 to 12+400.000", "12+600.000 to 12+800.000");
        }

        @Test
        void releasesAStretchOnceTheReportIsSettled() {
            List<ChainageRange> held =
                    Ncr.withheldExtent(new Ncr.WithholdQuery(NCRS, "WP-01", "place", d("2025-03-31")));
            assertThat(show(held)).containsExactly("12+200.000 to 12+400.000", "12+600.000 to 12+800.000");
        }

        @Test
        void appliesAReportWithNoStepNamedToEveryStep() {
            List<ChainageRange> held =
                    Ncr.withheldExtent(new Ncr.WithholdQuery(NCRS, "WP-01", "trim", d("2025-03-31")));
            assertThat(show(held)).containsExactly("12+600.000 to 12+800.000");
        }

        @Test
        void ignoresAReportThatDoesNotWithholdCredit() {
            Ncr advisory = Ncr.build(NcrInput.from(OPEN_NCR).withId("NCR-04").withWithholdsCredit(false));
            assertThat(Ncr.withheldExtent(new Ncr.WithholdQuery(List.of(advisory), "WP-01", "place", d("2025-03-31"))))
                    .isEmpty();
        }

        @Test
        void ignoresAReportAgainstAnotherPackage() {
            assertThat(Ncr.withheldExtent(new Ncr.WithholdQuery(NCRS, "WP-02", "place", d("2025-03-31"))))
                    .isEmpty();
        }
    }

    @Nested
    class ApplyWithholding {

        private final StepGate openGate =
                new StepGate("place", GateState.OPEN, List.of(r("12+000", "12+800")), List.of());

        @Test
        void leavesTheGateAloneWhenNothingIsWithheld() {
            assertThat(openGate.withholding(List.of())).isSameAs(openGate);
        }

        @Test
        void punchesTheWithheldStretchOutOfTheOpenExtent() {
            StepGate narrowed = openGate.withholding(List.of(r("12+200", "12+400")));
            assertThat(show(narrowed.openExtent()))
                    .containsExactly("12+000.000 to 12+200.000", "12+400.000 to 12+800.000");
            assertThat(narrowed.state()).isEqualTo(GateState.PART_OPEN);
        }

        @Test
        void closesTheGateWhenTheWholeExtentIsWithheld() {
            StepGate shut = openGate.withholding(List.of(r("12+000", "12+800")));
            assertThat(shut.openExtent()).isEmpty();
            assertThat(shut.state()).isEqualTo(GateState.HELD);
        }

        @Test
        void leavesAnAlreadyHeldGateHeld() {
            StepGate held = new StepGate("place", GateState.HELD, List.of(), List.of("IP-01"));
            assertThat(held.withholding(List.of(r("12+000", "12+800"))).state()).isEqualTo(GateState.HELD);
        }
    }

    @Nested
    class Describe {

        @Test
        void readsLikeARegisterLineForAnOpenReport() {
            assertThat(OPEN_NCR.describe())
                    .isEqualTo(
                            "NCR-01 raised 2025-03-05, open: fill placed at 350 mm layers against a 250 mm specification");
        }

        @Test
        void namesTheSettlementForAClosedReport() {
            assertThat(CLOSED_NCR.describe()).contains("closed 2025-03-14");
        }
    }
}
