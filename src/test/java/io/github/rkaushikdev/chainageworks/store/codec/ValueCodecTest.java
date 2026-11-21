package io.github.rkaushikdev.chainageworks.store.codec;

import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.arrayFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.booleanFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.codeFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.codeToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.currencyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.dateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.moneyToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.objectFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalDateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.optionalString;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.percentToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.quantityFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.quantityToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rangeFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.rateToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.stationFromJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.stationToJson;
import static io.github.rkaushikdev.chainageworks.store.codec.ValueCodec.unitFromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.chainage.ChainageRange;
import io.github.rkaushikdev.chainageworks.domain.chainage.Stations;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.money.Rate;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import io.github.rkaushikdev.chainageworks.util.Json;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValueCodecTest {

    /** The value written, turned to text, read back as a tree and decoded, as a save and a load do. */
    private static <T> T roundTrip(T value, Function<T, JsonNode> to, Function<JsonNode, T> from) {
        try {
            return from.apply(Json.parse(Json.stringify(to.apply(value))));
        } catch (Json.JsonFailure broken) {
            throw new AssertionError(broken);
        }
    }

    private static ObjectNode object(Map<String, ?> fields) {
        return (ObjectNode) Json.valueOf(fields);
    }

    private static ObjectNode money(Object minor, String currency) {
        ObjectNode json = Json.object();
        json.set("minor", Json.valueOf(minor));
        json.put("currency", currency);
        return json;
    }

    @Nested
    class Chainage {

        @Test
        void writesAStationTheWayADrawingDoes() {
            assertThat(stationToJson(Stations.parse("12+340.5"))).isEqualTo("12+340.500");
        }

        @Test
        void roundTripsAStationAndARange() {
            long station = Stations.parse("12+340.5");
            assertThat(roundTrip(station, s -> TextNode.valueOf(stationToJson(s)), ValueCodec::stationFromJson))
                    .isEqualTo(station);
            ChainageRange range = ChainageRange.of(Stations.parse("12+000"), Stations.parse("12+800"));
            assertThat(roundTrip(range, ValueCodec::rangeToJson, ValueCodec::rangeFromJson))
                    .isEqualTo(range);
        }

        @Test
        void refusesAStationThatIsNotAStringOrNotAChainage() {
            assertThatThrownBy(() -> stationFromJson(Json.number(12_340_500)))
                    .hasMessageContaining("expected a string");
            assertThatThrownBy(() -> stationFromJson(TextNode.valueOf("nonsense")))
                    .isInstanceOf(BadInputException.class);
        }

        @Test
        void refusesARangeThatIsNotAnObject() {
            assertThatThrownBy(() -> rangeFromJson(TextNode.valueOf("12+000")))
                    .hasMessageContaining("expected an object");
            assertThatThrownBy(() -> rangeFromJson(NullNode.getInstance())).hasMessageContaining("expected an object");
        }

        @Test
        void refusesARangeThatRunsBackwards() {
            assertThatThrownBy(() -> rangeFromJson(object(Map.of("from", "12+800", "to", "12+000"))))
                    .hasMessageContaining("runs forwards");
        }
    }

    @Nested
    class Dates {

        @Test
        void roundTrips() {
            LocalDate date = SiteDates.parse("2025-03-14");
            assertThat(dateToJson(date)).isEqualTo("2025-03-14");
            assertThat(roundTrip(date, day -> TextNode.valueOf(dateToJson(day)), ValueCodec::dateFromJson))
                    .isEqualTo(date);
        }

        @Test
        void carriesANullThrough() {
            assertThat(optionalDateToJson(Optional.empty()).isNull()).isTrue();
            assertThat(optionalDateFromJson(NullNode.getInstance())).isEmpty();
            assertThat(optionalDateFromJson(MissingNode.getInstance())).isEmpty();
            assertThat(optionalDateFromJson(TextNode.valueOf("2025-03-14"))).contains(SiteDates.parse("2025-03-14"));
        }

        @Test
        void refusesADateThatDoesNotExist() {
            assertThatThrownBy(() -> dateFromJson(TextNode.valueOf("2025-02-30")))
                    .hasMessageContaining("not in that month");
        }
    }

    @Nested
    class MoneyRatesQuantitiesAndPercentages {

        @Test
        void roundTripsMoneyExactly() {
            Money value = Money.fromMajor(1234.56, Currency.EUR);
            assertThat(moneyToJson(value)).isEqualTo(money(123_456, "EUR"));
            assertThat(roundTrip(value, ValueCodec::moneyToJson, ValueCodec::moneyFromJson))
                    .isEqualTo(value);
        }

        @Test
        void roundTripsARateWithFourDecimalPlaces() {
            Rate value = Rate.of(0.0035, Currency.GBP, Unit.KG);
            assertThat(rateToJson(value)).isEqualTo(object(Map.of("milliMinor", 350, "currency", "GBP", "unit", "kg")));
            assertThat(roundTrip(value, ValueCodec::rateToJson, ValueCodec::rateFromJson))
                    .isEqualTo(value);
        }

        @Test
        void roundTripsAQuantity() {
            Quantity value = Quantity.of(12.345, Unit.M3);
            assertThat(quantityToJson(value)).isEqualTo(object(Map.of("milli", 12_345, "unit", "m3")));
            assertThat(roundTrip(value, ValueCodec::quantityToJson, ValueCodec::quantityFromJson))
                    .isEqualTo(value);
        }

        @Test
        void roundTripsAPercentageAsBasisPoints() {
            assertThat(percentToJson(Percent.of(3.75))).isEqualTo(375);
            assertThat(percentFromJson(Json.number(375))).isEqualTo(Percent.of(3.75));
        }

        @Test
        void refusesAnUnknownCurrencyAndAnUnknownUnit() {
            assertThatThrownBy(() -> moneyFromJson(money(1, "JPY"))).hasMessageContaining("unknown currency");
            assertThatThrownBy(() -> currencyFromJson(TextNode.valueOf("JPY")))
                    .hasMessageContaining("unknown currency");
            assertThatThrownBy(() -> unitFromJson(TextNode.valueOf("furlong"))).hasMessageContaining("unknown unit");
            assertThatThrownBy(() -> quantityFromJson(object(Map.of("milli", 1, "unit", "furlong"))))
                    .hasMessageContaining("unknown unit");
        }

        @Test
        void refusesAnAmountThatIsNotANumber() {
            assertThatThrownBy(() -> moneyFromJson(money("100", "GBP"))).hasMessageContaining("expected a number");
            assertThatThrownBy(() -> percentFromJson(TextNode.valueOf("5"))).hasMessageContaining("expected a number");
            assertThatThrownBy(() -> moneyFromJson(money(Double.NaN, "GBP"))).hasMessageContaining("expected a number");
        }
    }

    @Nested
    class ItemCodes {

        @Test
        void roundTrips() {
            ItemCode code = ItemCode.parse("E423.1");
            assertThat(codeToJson(code)).isEqualTo("E423.1");
            assertThat(roundTrip(code, c -> TextNode.valueOf(codeToJson(c)), ValueCodec::codeFromJson))
                    .isEqualTo(code);
        }

        @Test
        void refusesACodeItCannotRead() {
            assertThatThrownBy(() -> codeFromJson(TextNode.valueOf("423"))).isInstanceOf(BadInputException.class);
        }
    }

    @Nested
    class TheShapeHelpers {

        @Test
        void checksObjectsListsAndFlags() {
            assertThat(objectFromJson(object(Map.of("a", 1)), "thing")).isEqualTo(object(Map.of("a", 1)));
            assertThat(arrayFromJson(Json.valueOf(List.of(1, 2)), "things"))
                    .containsExactly(Json.number(1), Json.number(2));
            assertThat(booleanFromJson(Json.valueOf(true), "flag")).isTrue();
        }

        @Test
        void namesWhatItWantedWhenTheShapeIsWrong() {
            assertThatCode(() -> objectFromJson(Json.array(), "thing")).doesNotThrowAnyException();
            assertThatThrownBy(() -> objectFromJson(TextNode.valueOf("x"), "thing"))
                    .hasMessageContaining("expected an object for the thing");
            assertThatThrownBy(() -> arrayFromJson(Json.object(), "things"))
                    .hasMessageContaining("expected a list for the things");
            assertThatThrownBy(() -> booleanFromJson(TextNode.valueOf("yes"), "flag"))
                    .hasMessageContaining("expected true or false for the flag");
        }

        @Test
        void defaultsAnAbsentString() {
            assertThat(optionalString(NullNode.getInstance())).isEmpty();
            assertThat(optionalString(MissingNode.getInstance(), "tape")).isEqualTo("tape");
            assertThat(optionalString(TextNode.valueOf("gps"), "tape")).isEqualTo("gps");
        }
    }
}
