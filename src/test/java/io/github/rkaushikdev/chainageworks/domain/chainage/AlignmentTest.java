package io.github.rkaushikdev.chainageworks.domain.chainage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.ConflictException;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment.ChainageEquation;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment.EquationKind;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment.Located;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment.Position;
import io.github.rkaushikdev.chainageworks.domain.chainage.Alignment.Segment;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlignmentTest {

    private static long at(String text) {
        return Stations.parse(text);
    }

    private static ChainageEquation eq(String back, String ahead) {
        return new ChainageEquation(at(back), at(ahead));
    }

    private static final Alignment PLAIN = Alignment.build("A1", "main carriageway", at("0+000"), at("4+000"));

    /** Thirty metres of chainage exist twice: 12+310 through 12+340. */
    private static final Alignment OVERLAPPING =
            Alignment.build("A2", "diverted section", at("12+000"), at("13+000"), List.of(eq("12+340", "12+310")));

    /** Twenty metres of chainage do not exist at all: 12+340 through 12+360. */
    private static final Alignment GAPPED =
            Alignment.build("A3", "shortened section", at("12+000"), at("13+000"), List.of(eq("12+340", "12+360")));

    @Nested
    class Kind {

        @Test
        void namesTheThreeShapes() {
            assertThat(eq("12+340", "12+310").kind()).isEqualTo(EquationKind.OVERLAP);
            assertThat(eq("12+340", "12+360").kind()).isEqualTo(EquationKind.GAP);
            assertThat(eq("12+340", "12+340").kind()).isEqualTo(EquationKind.CONTINUOUS);
        }
    }

    @Nested
    class Describe {

        @Test
        void writesTheSurveyorForm() {
            assertThat(eq("12+340", "12+310").describe()).isEqualTo("12+340.000 back = 12+310.000 ahead");
        }
    }

    @Nested
    class Segments {

        @Test
        void givesOneSegmentWhenThereAreNoEquations() {
            assertThat(PLAIN.segments()).containsExactly(new Segment(0, 0, 4_000_000, 0));
        }

        @Test
        void splitsAtEachEquationAndCarriesTheRunningDistance() {
            assertThat(OVERLAPPING.segments())
                    .containsExactly(
                            new Segment(0, 12_000_000, 12_340_000, 0), new Segment(1, 12_310_000, 13_000_000, 340_000));
        }

        @Test
        void refusesAnEquationThatDoesNotAdvance() {
            assertThatThrownBy(() -> Alignment.build(
                            "bad", "folded", at("12+000"), at("13+000"), List.of(eq("11+900", "12+500"))))
                    .hasMessageContaining("must sit ahead of the series it closes");
        }

        @Test
        void refusesAnEquationThatLandsExactlyOnTheSeriesStart() {
            assertThatThrownBy(() -> Alignment.build(
                            "bad", "zero length", at("12+000"), at("13+000"), List.of(eq("12+000", "12+500"))))
                    .isInstanceOf(BadInputException.class);
        }

        @Test
        void refusesAnAlignmentThatEndsBeforeItsLastSeriesStarts() {
            assertThatThrownBy(() -> Alignment.build(
                            "bad", "backwards end", at("12+000"), at("12+200"), List.of(eq("12+100", "12+500"))))
                    .hasMessageContaining("ends at or before");
        }

        @Test
        void handlesTwoEquationsInARow() {
            Alignment twice = Alignment.build(
                    "A4",
                    "twice diverted",
                    at("0+000"),
                    at("3+000"),
                    List.of(eq("1+000", "0+950"), eq("2+000", "2+100")));
            assertThat(twice.segments())
                    .containsExactly(
                            new Segment(0, 0, 1_000_000, 0),
                            new Segment(1, 950_000, 2_000_000, 1_000_000),
                            new Segment(2, 2_100_000, 3_000_000, 2_050_000));
        }
    }

    @Nested
    class Length {

        @Test
        void isThePlainDifferenceWithNoEquations() {
            assertThat(PLAIN.length()).isEqualTo(4_000_000);
        }

        @Test
        void countsTheOverlappedThirtyMetresTwiceBecauseItIsBuiltTwice() {
            assertThat(OVERLAPPING.length()).isEqualTo(1_030_000);
        }

        @Test
        void leavesOutTheTwentyMetresAGapSkips() {
            assertThat(GAPPED.length()).isEqualTo(980_000);
        }
    }

    @Nested
    class Locate {

        @Test
        void findsOnePlaceOnAContinuousAlignment() {
            assertThat(PLAIN.locate(at("1+500"))).containsExactly(new Position(0, 1_500_000));
        }

        @Test
        void findsBothPlacesInsideAnOverlap() {
            assertThat(OVERLAPPING.locate(at("12+320")))
                    .containsExactly(new Position(0, 320_000), new Position(1, 350_000));
        }

        @Test
        void findsNothingInsideAGap() {
            assertThat(GAPPED.locate(at("12+350"))).isEmpty();
        }

        @Test
        void findsNothingOffEitherEnd() {
            assertThat(PLAIN.locate(at("5+000"))).isEmpty();
            assertThat(OVERLAPPING.locate(at("11+000"))).isEmpty();
        }

        @Test
        void reportsTheSharedEndOfAContinuousEquationOnceByDistance() {
            Alignment continuous =
                    Alignment.build("A5", "paper equation", at("0+000"), at("2+000"), List.of(eq("1+000", "1+000")));
            assertThat(continuous.isAmbiguous(at("1+000"))).isFalse();
            assertThat(continuous.distanceAt(at("1+000"))).isEqualTo(1_000_000);
        }
    }

    @Nested
    class IsOnAlignment {

        @Test
        void answersForBothEndsAndForAGap() {
            assertThat(PLAIN.isOnAlignment(at("0+000"))).isTrue();
            assertThat(PLAIN.isOnAlignment(at("4+000"))).isTrue();
            assertThat(GAPPED.isOnAlignment(at("12+350"))).isFalse();
        }
    }

    @Nested
    class IsAmbiguous {

        @Test
        void isTrueOnlyInsideAnOverlap() {
            assertThat(OVERLAPPING.isAmbiguous(at("12+320"))).isTrue();
            assertThat(OVERLAPPING.isAmbiguous(at("12+100"))).isFalse();
            assertThat(OVERLAPPING.isAmbiguous(at("12+500"))).isFalse();
            assertThat(PLAIN.isAmbiguous(at("1+000"))).isFalse();
        }
    }

    @Nested
    class DistanceAt {

        @Test
        void measuresFromTheStartOfTheAlignment() {
            assertThat(PLAIN.distanceAt(at("1+500"))).isEqualTo(1_500_000);
        }

        @Test
        void refusesAStationThatIsNotOnTheAlignment() {
            assertThatThrownBy(() -> GAPPED.distanceAt(at("12+350"))).hasMessageContaining("not on this alignment");
        }

        @Test
        void refusesAnAmbiguousStationUntilASeriesIsNamed() {
            assertThatThrownBy(() -> OVERLAPPING.distanceAt(at("12+320"))).isInstanceOf(ConflictException.class);
            assertThat(OVERLAPPING.distanceAt(at("12+320"), 0)).isEqualTo(320_000);
            assertThat(OVERLAPPING.distanceAt(at("12+320"), 1)).isEqualTo(350_000);
        }

        @Test
        void refusesASeriesTheStationIsNotIn() {
            assertThatThrownBy(() -> OVERLAPPING.distanceAt(at("12+100"), 1))
                    .hasMessageContaining("not in that series");
        }
    }

    @Nested
    class TrueLength {

        @Test
        void isThePlainDifferenceWhenNothingJumps() {
            assertThat(PLAIN.trueLength(at("1+000"), at("1+500"))).isEqualTo(500_000);
        }

        @Test
        void dropsTheSkippedGroundAcrossAGap() {
            assertThat(GAPPED.trueLength(at("12+300"), at("12+400"))).isEqualTo(80_000);
        }

        @Test
        void isNegativeWhenTheStationsAreGivenTheWrongWayRound() {
            assertThat(PLAIN.trueLength(at("1+500"), at("1+000"))).isEqualTo(-500_000);
        }
    }

    @Nested
    class StationAtDistance {

        @Test
        void walksForwardOnAPlainAlignment() {
            assertThat(PLAIN.stationAtDistance(1_500_000)).isEqualTo(new Located(1_500_000, 0));
        }

        @Test
        void returnsTheEarlierSeriesAtABoundaryDistance() {
            assertThat(OVERLAPPING.stationAtDistance(340_000)).isEqualTo(new Located(12_340_000, 0));
        }

        @Test
        void crossesIntoTheNextSeries() {
            assertThat(OVERLAPPING.stationAtDistance(350_000)).isEqualTo(new Located(12_320_000, 1));
        }

        @Test
        void reachesTheFarEndExactly() {
            assertThat(PLAIN.stationAtDistance(4_000_000)).isEqualTo(new Located(4_000_000, 0));
        }

        @Test
        void refusesANegativeDistance() {
            assertThatThrownBy(() -> PLAIN.stationAtDistance(-1)).hasMessageContaining("must not be negative");
        }

        @Test
        void refusesADistancePastTheEnd() {
            assertThatThrownBy(() -> PLAIN.stationAtDistance(4_000_001)).hasMessageContaining("runs off the end");
        }
    }
}
