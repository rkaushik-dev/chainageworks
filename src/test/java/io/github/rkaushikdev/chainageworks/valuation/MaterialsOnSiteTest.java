package io.github.rkaushikdev.chainageworks.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.domain.time.SiteDates;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaterialsOnSiteTest {

    private static LocalDate d(String text) {
        return SiteDates.parse(text);
    }

    private static final MaterialsInput BASE = MaterialsInput.of(
                    "MOS-01",
                    "WP-02",
                    ItemCode.parse("I185.2"),
                    "900 mm concrete pipe, 240 m delivered to the compound",
                    Quantity.of(240, Unit.M),
                    Money.fromMajor(38_400, Currency.GBP),
                    d("2025-03-10"))
            .withVestedOn(d("2025-03-12"));

    private static final MaterialsOnSite PIPES = MaterialsOnSite.build(BASE);
    private static final LocalDate CUT_OFF = d("2025-03-31");

    private static List<String> ids(List<MaterialsOnSite> lots) {
        return lots.stream().map(MaterialsOnSite::id).toList();
    }

    @Nested
    class BuildMaterialsOnSite {

        @Test
        void defaultsToProtectedAndNeitherVestedNorBuiltIn() {
            MaterialsOnSite bare = MaterialsOnSite.build(BASE.withVestedOn(null));
            assertThat(bare.protectedOnSite()).isTrue();
            assertThat(bare.vestedOn()).isEmpty();
            assertThat(bare.incorporatedOn()).isEmpty();
        }

        @Test
        void trimsTheDescriptionAndRefusesAnEmptyOne() {
            assertThat(MaterialsOnSite.build(BASE.withDescription("  pipe  ")).description())
                    .isEqualTo("pipe");
            assertThatThrownBy(() -> MaterialsOnSite.build(BASE.withDescription(" ")))
                    .hasMessageContaining("need a description");
        }

        @Test
        void refusesANegativeValue() {
            assertThatThrownBy(() -> MaterialsOnSite.build(BASE.withValue(Money.fromMajor(-1, Currency.GBP))))
                    .hasMessageContaining("negative value");
        }

        @Test
        void refusesVestingOrIncorporationBeforeDelivery() {
            assertThatThrownBy(() -> MaterialsOnSite.build(BASE.withVestedOn(d("2025-03-01"))))
                    .hasMessageContaining("vest before they were delivered");
            assertThatThrownBy(() -> MaterialsOnSite.build(BASE.withIncorporatedOn(d("2025-03-01"))))
                    .hasMessageContaining("built in before they were delivered");
        }
    }

    @Nested
    class ReasonNotClaimable {

        @Test
        void passesEverythingThatHasBeenDeliveredVestedAndProtected() {
            assertThat(PIPES.reasonNotClaimable(CUT_OFF)).isEmpty();
            assertThat(PIPES.isClaimableAt(CUT_OFF)).isTrue();
        }

        @Test
        void rejectsMaterialThatHasNotArrivedYet() {
            assertThat(PIPES.reasonNotClaimable(d("2025-03-09"))).contains(NotClaimableReason.NOT_DELIVERED);
        }

        @Test
        void rejectsMaterialWhoseTitleHasNotPassed() {
            assertThat(PIPES.reasonNotClaimable(d("2025-03-11"))).contains(NotClaimableReason.NOT_VESTED);
            MaterialsOnSite unvested = MaterialsOnSite.build(BASE.withVestedOn(null));
            assertThat(unvested.reasonNotClaimable(CUT_OFF)).contains(NotClaimableReason.NOT_VESTED);
        }

        @Test
        void rejectsMaterialThatIsNotProtected() {
            MaterialsOnSite exposed = MaterialsOnSite.build(BASE.withProtectedOnSite(false));
            assertThat(exposed.reasonNotClaimable(CUT_OFF)).contains(NotClaimableReason.NOT_PROTECTED);
        }

        @Test
        void rejectsMaterialThatIsAlreadyInTheWorksWhichIsTheDoubleCount() {
            MaterialsOnSite built = MaterialsOnSite.build(BASE.withIncorporatedOn(d("2025-03-20")));
            assertThat(built.reasonNotClaimable(CUT_OFF)).contains(NotClaimableReason.INCORPORATED);
            assertThat(built.reasonNotClaimable(d("2025-03-19"))).isEmpty();
        }

        @Test
        void reportsTheFirstFailingConditionNotAllOfThem() {
            MaterialsOnSite nothing =
                    MaterialsOnSite.build(BASE.withVestedOn(null).withProtectedOnSite(false));
            assertThat(nothing.reasonNotClaimable(d("2025-03-01"))).contains(NotClaimableReason.NOT_DELIVERED);
        }
    }

    @Nested
    class MaterialsValue {

        private final MaterialsOnSite cement = MaterialsOnSite.build(BASE.withId("MOS-02")
                .withDescription("cement, 40 tonnes in silo")
                .withQuantity(Quantity.of(40, Unit.T))
                .withValue(Money.fromMajor(6000, Currency.GBP))
                .withDeliveredOn(d("2025-03-18"))
                .withVestedOn(d("2025-03-18")));

        @Test
        void takesTheContractAllowanceOffTheInvoiceValue() {
            assertThat(MaterialsOnSite.value(List.of(PIPES), CUT_OFF, Currency.GBP)
                            .major())
                    .isEqualTo(34_560);
        }

        @Test
        void addsEveryClaimableLot() {
            assertThat(MaterialsOnSite.value(List.of(PIPES, cement), CUT_OFF, Currency.GBP)
                            .major())
                    .isEqualTo(34_560 + 5400);
        }

        @Test
        void honoursADifferentAllowance() {
            assertThat(MaterialsOnSite.value(List.of(PIPES), CUT_OFF, Currency.GBP, Percent.of(100))
                            .major())
                    .isEqualTo(38_400);
            assertThat(MaterialsOnSite.value(List.of(PIPES), CUT_OFF, Currency.GBP, Percent.of(0))
                            .major())
                    .isEqualTo(0);
        }

        @Test
        void leavesOutAnythingThatCannotBeClaimed() {
            MaterialsOnSite built = MaterialsOnSite.build(BASE.withIncorporatedOn(d("2025-03-20")));
            assertThat(MaterialsOnSite.value(List.of(built, cement), CUT_OFF, Currency.GBP)
                            .major())
                    .isEqualTo(5400);
        }

        @Test
        void isNothingWhenTheListIsEmptyOrNothingQualifies() {
            assertThat(MaterialsOnSite.value(List.of(), CUT_OFF, Currency.GBP).minor())
                    .isZero();
            assertThat(MaterialsOnSite.value(List.of(PIPES), d("2025-01-01"), Currency.GBP)
                            .minor())
                    .isZero();
        }

        @Test
        void listsWhatQualifies() {
            assertThat(ids(MaterialsOnSite.claimable(List.of(PIPES, cement), d("2025-03-15"))))
                    .containsExactly("MOS-01");
        }
    }

    @Nested
    class MaterialsMovement {

        private final MaterialsOnSite built = MaterialsOnSite.build(BASE.withIncorporatedOn(d("2025-04-10")));
        private final MaterialsOnSite arriving = MaterialsOnSite.build(
                BASE.withId("MOS-03").withDeliveredOn(d("2025-04-05")).withVestedOn(d("2025-04-05")));

        @Test
        void reportsWhatArrivedAndWhatWasBuiltInBetweenTwoCutOffs() {
            MaterialsOnSite.Movement movement =
                    MaterialsOnSite.movement(List.of(built, arriving), CUT_OFF, d("2025-04-30"));
            assertThat(ids(movement.added())).containsExactly("MOS-03");
            assertThat(ids(movement.removed())).containsExactly("MOS-01");
        }

        @Test
        void reportsNothingWhenNothingMoved() {
            MaterialsOnSite.Movement movement = MaterialsOnSite.movement(List.of(PIPES), CUT_OFF, d("2025-04-30"));
            assertThat(movement.added()).isEmpty();
            assertThat(movement.removed()).isEmpty();
        }
    }

    @Nested
    class DescribeMaterials {

        @Test
        void readsLikeAScheduleLine() {
            assertThat(PIPES.describe())
                    .isEqualTo("MOS-01 900 mm concrete pipe, 240 m delivered to the compound, delivered 2025-03-10");
        }
    }
}
