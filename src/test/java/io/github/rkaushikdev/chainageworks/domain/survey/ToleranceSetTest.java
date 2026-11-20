package io.github.rkaushikdev.chainageworks.domain.survey;

import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.pkg;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.samplePackages;
import static io.github.rkaushikdev.chainageworks.support.PackageFixtures.share;
import static io.github.rkaushikdev.chainageworks.support.SurveyFixtures.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.packages.PackageRegister;
import io.github.rkaushikdev.chainageworks.domain.packages.WorkPackage;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet.OverMeasure;
import io.github.rkaushikdev.chainageworks.domain.survey.ToleranceSet.Rule;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.support.PackageFixtures.PackageSpec;
import io.github.rkaushikdev.chainageworks.support.SurveyFixtures.RecordSpec;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToleranceSetTest {

    private static final PackageRegister REGISTER = PackageRegister.build(samplePackages());
    private static final LocalDate AS_AT = SiteDates.parse("2025-03-31");
    private static final ToleranceSet DEFAULTS = ToleranceSet.DEFAULT;

    private static ItemCode c(String text) {
        return ItemCode.parse(text);
    }

    private static RecordSpec excavation(String id, String from, String to, double amount, String on) {
        return RecordSpec.of(id, "E423.1", "excavate", from, to, amount, Unit.M3, on);
    }

    @Nested
    class TheToleranceSet {

        @Test
        void readsAClassRuleWhereThereIsOne() {
            assertThat(DEFAULTS.allowanceFor(c("E423.1")).value()).isEqualTo(10);
            assertThat(DEFAULTS.allowanceFor(c("K132.1")).value()).isZero();
        }

        @Test
        void fallsBackForAClassWithNoRuleOfItsOwn() {
            assertThat(DEFAULTS.allowanceFor(c("R712.1")).value()).isEqualTo(2.5);
            assertThat(DEFAULTS.allowanceFor(c("F624.1")).value()).isEqualTo(2.5);
        }

        @Test
        void refusesTwoRulesCoveringTheSameClass() {
            assertThatThrownBy(() -> ToleranceSet.build(List.of(
                            Rule.fallback(Percent.of(1)),
                            Rule.forClass("E", Percent.of(5)),
                            Rule.forClass("E", Percent.of(10)))))
                    .hasMessageContaining("same work class");
        }

        @Test
        void refusesASetWithNoFallback() {
            assertThatThrownBy(() -> ToleranceSet.build(List.of(Rule.forClass("E", Percent.of(5)))))
                    .hasMessageContaining("needs a fallback rule");
        }

        @Test
        void refusesANegativeAllowance() {
            assertThatThrownBy(() -> ToleranceSet.build(List.of(Rule.fallback(Percent.of(-1)))))
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        void acceptsASetBuiltFromTheDefaults() {
            assertThat(ToleranceSet.build(DEFAULTS.rules()).rules()).hasSize(5);
        }
    }

    @Nested
    class AllowedQuantity {

        private final WorkPackage earthworksOne = samplePackages().get(0);

        @Test
        void addsTheClassAllowanceToTheAllocation() {
            assertThat(DEFAULTS.allowedQuantity(earthworksOne, c("E423.1"))
                            .orElseThrow()
                            .format())
                    .isEqualTo("7700.00 m3");
        }

        @Test
        void givesNothingForAnItemThePackageIsNotAllocated() {
            assertThat(DEFAULTS.allowedQuantity(earthworksOne, c("R712.1"))).isEmpty();
        }
    }

    @Nested
    class FindOverMeasures {

        @Test
        void findsNothingWhenEverythingIsInsideTheAllowance() {
            List<SurveyRecord> book = records(excavation("SR-01", "12+000", "12+800", 7500, "2025-03-10"));
            assertThat(DEFAULTS.findOverMeasures(book, REGISTER, AS_AT)).isEmpty();
        }

        @Test
        void reportsAMeasurementPastTheAllowance() {
            List<SurveyRecord> book = records(excavation("SR-01", "12+000", "12+800", 8200, "2025-03-10"));
            List<OverMeasure> found = DEFAULTS.findOverMeasures(book, REGISTER, AS_AT);
            assertThat(found).hasSize(1);
            assertThat(found.get(0).packageId()).isEqualTo("WP-01");
            assertThat(found.get(0).excess().format()).isEqualTo("500.00 m3");
            assertThat(found.get(0).message())
                    .isEqualTo("WP-01 E423.1 measured 8200.00 m3 against an allowance of 7700.00 m3 "
                            + "(7000.00 m3 plus 10%)");
        }

        @Test
        void appliesTheTighterAllowanceAPavementItemGets() {
            List<SurveyRecord> book =
                    records(RecordSpec.of("SR-01", "R712.1", "subbase", "12+000", "13+600", 4200, Unit.M2, "2025-03-10")
                            .inPackage("WP-03"));
            List<OverMeasure> found = DEFAULTS.findOverMeasures(book, REGISTER, AS_AT);
            assertThat(found.stream().map(o -> o.code().format())).containsExactly("R712.1");
            assertThat(found.get(0).allowed().format()).isEqualTo("4100.00 m2");
        }

        @Test
        void addsRecordsAcrossAPackageBeforeItDecides() {
            List<SurveyRecord> book = records(
                    excavation("SR-01", "12+000", "12+400", 4000, "2025-03-10"),
                    excavation("SR-02", "12+400", "12+800", 4000, "2025-03-20"));
            assertThat(DEFAULTS.findOverMeasures(book, REGISTER, AS_AT)).hasSize(1);
        }

        @Test
        void ignoresRecordsThatAreNotInDateYet() {
            List<SurveyRecord> book = records(excavation("SR-01", "12+000", "12+800", 8200, "2025-04-10"));
            assertThat(DEFAULTS.findOverMeasures(book, REGISTER, AS_AT)).isEmpty();
        }

        @Test
        void honoursASetWithADifferentAllowance() {
            ToleranceSet strict = ToleranceSet.build(List.of(Rule.fallback(Percent.of(0))));
            List<SurveyRecord> book = records(excavation("SR-01", "12+000", "12+800", 7001, "2025-03-10"));
            assertThat(strict.findOverMeasures(book, REGISTER, AS_AT)).hasSize(1);
        }
    }

    @Nested
    class FindRecordsOutsideExtent {

        @Test
        void findsARecordBookedPastTheEndOfItsPackage() {
            List<SurveyRecord> book = records(excavation("SR-01", "12+000", "13+000", 100, "2025-03-10"));
            assertThat(ToleranceSet.findRecordsOutsideExtent(book, REGISTER).stream()
                            .map(SurveyRecord::id))
                    .containsExactly("SR-01");
        }

        @Test
        void findsNothingWhenTheRecordSitsInside() {
            List<SurveyRecord> book = records(excavation("SR-01", "12+100", "12+700", 100, "2025-03-10"));
            assertThat(ToleranceSet.findRecordsOutsideExtent(book, REGISTER)).isEmpty();
        }

        @Test
        void saysNothingAboutARecordWhosePackageIsMissing() {
            List<SurveyRecord> book = records(
                    excavation("SR-01", "0+000", "9+000", 100, "2025-03-10").inPackage("WP-99"));
            assertThat(ToleranceSet.findRecordsOutsideExtent(book, REGISTER)).isEmpty();
            assertThat(ToleranceSet.findOrphanRecords(book, REGISTER).stream().map(SurveyRecord::id))
                    .containsExactly("SR-01");
        }
    }

    @Nested
    class FindOverMeasuresAgainstAnEmptyRegister {

        @Test
        void reportsNothingAtAll() {
            PackageRegister empty = PackageRegister.build(List.of());
            List<SurveyRecord> book = records(excavation("SR-01", "12+000", "12+800", 99_999, "2025-03-10"));
            assertThat(DEFAULTS.findOverMeasures(book, empty, AS_AT)).isEmpty();
            assertThat(ToleranceSet.findOrphanRecords(book, empty).stream().map(SurveyRecord::id))
                    .containsExactly("SR-01");
        }

        @Test
        void doesNotFireOnAPackageNothingWasMeasuredAgainst() {
            PackageRegister lonely = PackageRegister.build(
                    List.of(pkg(PackageSpec.of("WP-50", "0+000", "0+100", share("E423.1", 10, Unit.M3)))));
            assertThat(DEFAULTS.findOverMeasures(List.of(), lonely, AS_AT)).isEmpty();
        }
    }
}
