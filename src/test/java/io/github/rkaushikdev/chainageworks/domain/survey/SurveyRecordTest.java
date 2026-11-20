package io.github.rkaushikdev.chainageworks.domain.survey;

import static io.github.rkaushikdev.chainageworks.support.SurveyFixtures.record;
import static io.github.rkaushikdev.chainageworks.support.SurveyFixtures.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.support.SurveyFixtures.RecordSpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SurveyRecordTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final List<SurveyRecord> BOOK = records(
            RecordSpec.of("SR-01", "E423.1", "excavate", "12+000", "12+200", 1800, Unit.M3, "2025-03-06"),
            RecordSpec.of("SR-02", "E423.1", "excavate", "12+200", "12+400", 1750, Unit.M3, "2025-03-13"),
            RecordSpec.of("SR-03", "E423.1", "excavate", "12+000", "12+200", 1920, Unit.M3, "2025-04-08")
                    .supersedes("SR-01"),
            RecordSpec.of("SR-04", "E532.1", "place", "12+000", "12+400", 2400, Unit.M3, "2025-03-20")
                    .inPackage("WP-01"),
            RecordSpec.of("SR-05", "E423.1", "excavate", "12+800", "13+000", 900, Unit.M3, "2025-03-25")
                    .inPackage("WP-02"));

    private static RecordSpec small(String id, String on) {
        return RecordSpec.of(id, "E423.1", "excavate", "0+000", "0+100", 1, Unit.M3, on);
    }

    private static List<String> ids(List<SurveyRecord> records) {
        return records.stream().map(SurveyRecord::id).toList();
    }

    @Nested
    class Build {

        @Test
        void defaultsTheMethodToATapeAndTheNoteToNothing() {
            SurveyRecord r = record(small("SR-99", "2025-03-01"));
            assertThat(r.method()).isEqualTo(MeasurementMethod.TAPE);
            assertThat(r.note()).isEmpty();
            assertThat(r.supersedes()).isEmpty();
            assertThat(r.isRemeasure()).isFalse();
        }

        @Test
        void refusesARecordWithNoIdAndOneWithNobodyNamed() {
            ChainageRange extent = ChainageRange.of(Stations.parse("0+000"), Stations.parse("0+100"));
            ItemCode code = ItemCode.parse("E423.1");
            Quantity one = Quantity.of(1, Unit.M3);
            assertThatThrownBy(() -> SurveyRecord.build(
                            SurveyRecordInput.of(" ", "WP-01", code, "excavate", extent, one, d("2025-03-01"), "J")))
                    .hasMessageContaining("needs an id");
            assertThatThrownBy(() -> SurveyRecord.build(
                            SurveyRecordInput.of("SR", "WP-01", code, "excavate", extent, one, d("2025-03-01"), "  ")))
                    .hasMessageContaining("name who booked it");
        }

        @Test
        void refusesARecordThatSupersedesItself() {
            assertThatThrownBy(() -> record(small("SR-98", "2025-03-01").supersedes("SR-98")))
                    .hasMessageContaining("cannot supersede itself");
        }

        @Test
        void refusesTwoRecordsSharingAnId() {
            assertThatThrownBy(() -> SurveyRecord.index(List.of(BOOK.get(0), BOOK.get(0))))
                    .hasMessageContaining("share an id");
        }
    }

    @Nested
    class SupersessionChain {

        @Test
        void walksBackToTheFirstBooking() {
            assertThat(ids(SurveyRecord.supersessionChain(BOOK, "SR-03"))).containsExactly("SR-03", "SR-01");
        }

        @Test
        void isASingleRecordWhenNothingWasSuperseded() {
            assertThat(ids(SurveyRecord.supersessionChain(BOOK, "SR-02"))).containsExactly("SR-02");
        }

        @Test
        void stopsWhenTheRecordItNamesIsNotInTheSet() {
            SurveyRecord orphan = record(small("SR-90", "2025-03-01").supersedes("SR-missing"));
            assertThat(ids(SurveyRecord.supersessionChain(List.of(orphan), "SR-90")))
                    .containsExactly("SR-90");
        }

        @Test
        void refusesALoopRatherThanSpinning() {
            SurveyRecord a = record(small("A", "2025-03-01").supersedes("B"));
            SurveyRecord b = record(small("B", "2025-03-02").supersedes("A"));
            assertThatThrownBy(() -> SurveyRecord.supersessionChain(List.of(a, b), "A"))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class FindDoubleSupersessions {

        @Test
        void findsNothingInACleanBook() {
            assertThat(SurveyRecord.findDoubleSupersessions(BOOK)).isEmpty();
        }

        @Test
        void findsARecordTwoRemeasuresBothClaimToReplace() {
            List<SurveyRecord> withRival = new ArrayList<>(BOOK);
            withRival.add(
                    record(RecordSpec.of("SR-06", "E423.1", "excavate", "12+000", "12+200", 1880, Unit.M3, "2025-04-09")
                            .supersedes("SR-01")));
            assertThat(SurveyRecord.findDoubleSupersessions(withRival)).containsExactly("SR-01");
        }
    }

    @Nested
    class Effective {

        @Test
        void leavesOutAnythingMeasuredAfterTheDate() {
            assertThat(ids(SurveyRecord.effective(BOOK, d("2025-03-10")))).containsExactly("SR-01");
        }

        @Test
        void keepsARecordWhoseRemeasureHasNotHappenedYet() {
            List<String> march = ids(SurveyRecord.effective(BOOK, d("2025-03-31")));
            assertThat(march).contains("SR-01").doesNotContain("SR-03");
        }

        @Test
        void dropsTheSupersededRecordOnceTheRemeasureIsInDate() {
            List<String> april = ids(SurveyRecord.effective(BOOK, d("2025-04-30")));
            assertThat(april).doesNotContain("SR-01").contains("SR-03");
        }
    }

    @Nested
    class Select {

        @Test
        void filtersByPackageItemAndStep() {
            LocalDate asAt = d("2025-03-31");
            assertThat(ids(SurveyRecord.select(BOOK, asAt, RecordQuery.ALL.withPackage("WP-02"))))
                    .containsExactly("SR-05");
            assertThat(ids(SurveyRecord.select(BOOK, asAt, RecordQuery.ALL.withCode(ItemCode.parse("E532.1")))))
                    .containsExactly("SR-04");
            assertThat(ids(SurveyRecord.select(BOOK, asAt, RecordQuery.ALL.withStep("place"))))
                    .containsExactly("SR-04");
        }

        @Test
        void sortsByTheDateMeasured() {
            assertThat(ids(SurveyRecord.select(BOOK, d("2025-03-31"))))
                    .containsExactly("SR-01", "SR-02", "SR-04", "SR-05");
        }

        @Test
        void takesAnEmptyQueryAsEverything() {
            assertThat(SurveyRecord.select(BOOK, d("2025-03-10"))).hasSize(1);
        }

        @Test
        void matchesAQueryDirectlyToo() {
            SurveyRecord first = BOOK.get(0);
            assertThat(first.matches(RecordQuery.ALL.withPackage("WP-01").withStep("excavate")))
                    .isTrue();
            assertThat(first.matches(RecordQuery.ALL.withPackage("WP-02"))).isFalse();
            assertThat(first.matches(RecordQuery.ALL.withCode(ItemCode.parse("E532.1"))))
                    .isFalse();
        }
    }

    @Nested
    class MeasuredQuantity {

        private final RecordQuery query = RecordQuery.ALL
                .withPackage("WP-01")
                .withCode(ItemCode.parse("E423.1"))
                .withStep("excavate");

        @Test
        void addsTheRecordsThatCountOnTheDate() {
            assertThat(SurveyRecord.measuredQuantity(BOOK, d("2025-03-31"), query)
                            .orElseThrow()
                            .format())
                    .isEqualTo("3550.00 m3");
        }

        @Test
        void picksUpTheRemeasureOnceItIsInDate() {
            assertThat(SurveyRecord.measuredQuantity(BOOK, d("2025-04-30"), query)
                            .orElseThrow()
                            .format())
                    .isEqualTo("3670.00 m3");
        }

        @Test
        void doesNotLetALaterRemeasureChangeAnEarlierAnswer() {
            assertThat(SurveyRecord.measuredQuantity(BOOK, d("2025-03-31"), query)
                            .orElseThrow()
                            .format())
                    .isEqualTo("3550.00 m3");
        }

        @Test
        void givesNothingWhenNothingMatched() {
            assertThat(SurveyRecord.measuredQuantity(BOOK, d("2025-01-01"), query))
                    .isEmpty();
            assertThat(SurveyRecord.measuredQuantity(BOOK, d("2025-04-30"), RecordQuery.ALL.withPackage("WP-09")))
                    .isEmpty();
        }
    }
}
