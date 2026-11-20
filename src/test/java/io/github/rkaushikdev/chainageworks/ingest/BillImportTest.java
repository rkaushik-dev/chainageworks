package io.github.rkaushikdev.chainageworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItem;
import io.github.rkaushikdev.chainageworks.domain.bill.BillItemKind;
import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.support.BillFixtures;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillImportTest {

    private static final String HEADER = String.join(",", BillImport.BILL_COLUMNS);
    private static final String EXCAVATION = "E423.1,excavation for cuttings,m3,4.85,12400";

    private static String sheet(String... rows) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(List.of(rows));
        return String.join("\n", lines);
    }

    @Nested
    class ImportItems {

        @Test
        void readsACleanRow() {
            ImportReport<BillItem> report = BillImport.importItems(sheet(EXCAVATION));
            assertThat(report.hasErrors()).isFalse();
            BillItem item = report.imported().get(0);
            assertThat(item.code().format()).isEqualTo("E423.1");
            assertThat(item.description()).isEqualTo("excavation for cuttings");
            assertThat(item.rate().major()).isEqualTo(4.85);
            assertThat(item.quantity().format()).isEqualTo("12400.00 m3");
            assertThat(item.kind()).isEqualTo(BillItemKind.MEASURED);
            assertThat(item.chainage()).isEmpty();
        }

        @Test
        void readsARateWithACurrencySymbolAndSeparators() {
            ImportReport<BillItem> report = BillImport.importItems(sheet("A213.1,establish site,sum,\"£48,500.00\",1"));
            assertThat(report.imported().get(0).rate().major()).isEqualTo(48_500);
        }

        @Test
        void defaultsASumItemToALumpAndEverythingElseToMeasured() {
            ImportReport<BillItem> report =
                    BillImport.importItems(sheet(EXCAVATION, "A213.1,establish site,sum,48500,1"));
            assertThat(report.imported().stream().map(BillItem::kind))
                    .containsExactly(BillItemKind.MEASURED, BillItemKind.LUMP);
        }

        @Test
        void readsTheKindColumnWhenItIsThere() {
            ImportReport<BillItem> report = BillImport.importItems(
                    String.join("\n", HEADER + ",kind", "E423.9,rock excavation,m3,28,500,provisional"));
            assertThat(report.imported().get(0).kind()).isEqualTo(BillItemKind.PROVISIONAL);
        }

        @Test
        void readsAKindWrittenWithASpace() {
            ImportReport<BillItem> report = BillImport.importItems(
                    String.join("\n", HEADER + ",kind", "A253.1,undertaker diversion,sum,120000,1,prime cost"));
            assertThat(report.imported().get(0).kind()).isEqualTo(BillItemKind.PRIME_COST);
        }

        @Test
        void rejectsAKindItDoesNotKnow() {
            ImportReport<BillItem> report = BillImport.importItems(
                    String.join("\n", HEADER + ",kind", "E423.1,excavation,m3,4.85,100,guesswork"));
            assertThat(report.errors().get(0).column()).contains("kind");
        }

        @Test
        void readsTheChainageColumnsWhenBothAreThere() {
            ImportReport<BillItem> report = BillImport.importItems(
                    String.join("\n", HEADER + ",from,to", "R712.1,sub base,m2,11.4,4000,12+000,12+800"));
            assertThat(report.imported().get(0).chainage().orElseThrow().format())
                    .isEqualTo("12+000.000 to 12+800.000");
        }

        @Test
        void leavesTheChainageEmptyWhenOnlyOneEndIsGiven() {
            ImportReport<BillItem> report = BillImport.importItems(
                    String.join("\n", HEADER + ",from,to", "R712.1,sub base,m2,11.4,4000,12+000,"));
            assertThat(report.imported().get(0).chainage()).isEmpty();
        }

        @Test
        void rejectsADuplicateCodeAndKeepsTheFirst() {
            ImportReport<BillItem> report = BillImport.importItems(sheet(EXCAVATION, EXCAVATION));
            assertThat(report.imported()).hasSize(1);
            assertThat(report.errors().get(0).message()).isEqualTo("item E423.1 appears twice");
        }

        @Test
        void rejectsALumpItemThatIsNotMeasuredInASum() {
            ImportReport<BillItem> report = BillImport.importItems(
                    String.join("\n", HEADER + ",kind", "A213.1,establish site,m3,48500,1,lump"));
            assertThat(report.errors().get(0).message()).contains("billed as a sum");
        }

        @Test
        void rejectsANegativeQuantityAndAnEmptyDescription() {
            assertThat(BillImport.importItems(sheet("E423.1,excavation,m3,4.85,-1"))
                            .errors())
                    .hasSize(1);
            assertThat(BillImport.importItems(sheet("E423.1,,m3,4.85,100"))
                            .errors()
                            .get(0)
                            .column())
                    .contains("description");
        }

        @Test
        void namesEveryMissingColumnOnce() {
            ImportReport<BillItem> report = BillImport.importItems("code,description\nE423.1,excavation");
            assertThat(report.issues()).hasSize(3);
            assertThat(report.imported()).isEmpty();
        }
    }

    @Nested
    class ImportBill {

        @Test
        void buildsAWholeBill() {
            Bill bill = BillImport.importBill(
                    sheet(EXCAVATION, "E532.1,filling,m3,3.15,9800"), new BillImport.Options("B9", "test bill", null));
            assertThat(bill.id()).isEqualTo("B9");
            assertThat(bill.items()).hasSize(2);
            assertThat(bill.total().major()).isEqualTo(12_400 * 4.85 + 9800 * 3.15);
        }

        @Test
        void throwsOnTheFirstErrorRatherThanReturningAPartBill() {
            assertThatThrownBy(() -> BillImport.importBill(sheet("nonsense,excavation,m3,4.85,100")))
                    .hasMessageContaining("line 2");
        }

        @Test
        void reportsAMissingColumnWithNoLineNumber() {
            assertThatThrownBy(() -> BillImport.importBill("code\nE423.1"))
                    .hasMessageContaining("the file has no description column");
        }

        @Test
        void takesACurrencyOtherThanSterling() {
            Bill bill = BillImport.importBill(sheet(EXCAVATION), new BillImport.Options(null, null, Currency.EUR));
            assertThat(bill.currency()).isEqualTo(Currency.EUR);
            assertThat(bill.require(ItemCode.parse("E423.1")).rate().currency()).isEqualTo(Currency.EUR);
        }
    }

    @Nested
    class ExportBill {

        @Test
        void writesAHeaderAndOneLinePerItem() {
            String text = BillImport.exportBill(BillFixtures.sampleBill());
            List<String> lines = Text.split(Text.trimEnd(text), "\n");
            assertThat(lines.get(0)).isEqualTo("code,description,unit,rate,quantity,kind,from,to");
            assertThat(lines).hasSize(9);
        }

        @Test
        void roundTripsThroughTheImporter() {
            Bill original = BillFixtures.sampleBill();
            Bill again = BillImport.importBill(
                    BillImport.exportBill(original),
                    new BillImport.Options(original.id(), original.title(), original.currency()));
            assertThat(again.total()).isEqualTo(original.total());
            assertThat(again.items().stream().map(i -> i.code().format()))
                    .containsExactlyElementsOf(original.items().stream()
                            .map(i -> i.code().format())
                            .toList());
            assertThat(again.items().stream().map(BillItem::kind))
                    .containsExactlyElementsOf(
                            original.items().stream().map(BillItem::kind).toList());
        }
    }
}
