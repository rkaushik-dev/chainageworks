package io.github.rkaushikdev.chainageworks.reporting;

import static io.github.rkaushikdev.chainageworks.support.ContextFixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.chainageworks.domain.bill.Bill;
import io.github.rkaushikdev.chainageworks.domain.bill.BillHistory;
import io.github.rkaushikdev.chainageworks.domain.bill.BillRevision;
import io.github.rkaushikdev.chainageworks.domain.quantity.Unit;
import io.github.rkaushikdev.chainageworks.support.BillFixtures;
import io.github.rkaushikdev.chainageworks.support.BillFixtures.ItemSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevisionReportTest {

    private static Bill revised(double rockRate, double rockQuantity) {
        return BillFixtures.sampleBill()
                .withItem(BillFixtures.item(ItemSpec.of("E423.1", rockRate, rockQuantity, Unit.M3)))
                .withItem(BillFixtures.item(ItemSpec.of("E532.1", 3.15, 10_400, Unit.M3)));
    }

    /** Rev B moves a rate and a quantity; Rev C puts the rate back and remeasures the same item. */
    private static final BillHistory HISTORY = BillHistory.build(List.of(
            new BillRevision("Rev A", d("2025-01-01"), BillFixtures.sampleBill(), "contract bill at award"),
            new BillRevision("Rev B", d("2025-04-01"), revised(5.2, 12_400), "excavation repriced"),
            new BillRevision("Rev C", d("2025-06-01"), revised(4.85, 13_000), "excavation remeasured")));

    private static final RevisionReport REPORT = RevisionReport.build(HISTORY);

    @Test
    void rendersEveryRevisionAgainstTheOneBeforeIt() {
        assertThat(REPORT.render())
                .isEqualTo(String.join(
                        "\n",
                        "bill revisions",
                        "==============",
                        "",
                        "steps                    2",
                        "steps that moved a rate  2",
                        "",
                        "Rev A to Rev B, effective 2025-04-01",
                        "------------------------------------",
                        "changes       2",
                        "net movement  £6,230.00",
                        "rates moved   yes",
                        "code    change            rate was  rate now  quantity was  quantity now   movement",
                        "------  ----------------  --------  --------  ------------  ------------  ---------",
                        "E423.1  rate changed      £4.85/m3  £5.20/m3   12400.00 m3   12400.00 m3  £4,340.00",
                        "E532.1  quantity changed  £3.15/m3  £3.15/m3    9800.00 m3   10400.00 m3  £1,890.00",
                        "",
                        "Rev B to Rev C, effective 2025-06-01",
                        "------------------------------------",
                        "changes       1",
                        "net movement  -£1,430.00",
                        "rates moved   yes",
                        "code    change                     rate was  rate now  quantity was  quantity now    movement",
                        "------  -------------------------  --------  --------  ------------  ------------  ----------",
                        "E423.1  rate and quantity changed  £5.20/m3  £4.85/m3   12400.00 m3   13000.00 m3"
                                + "  -£1,430.00"));
    }

    @Test
    void tellsTheSignatoryEveryRateThatMoved() {
        assertThat(REPORT.rateChanges())
                .containsExactly("Rev B: E423.1 £4.85/m3 to £5.20/m3", "Rev C: E423.1 £5.20/m3 to £4.85/m3");
    }

    @Test
    void hasNoStepsForASingleRevision() {
        RevisionReport single = RevisionReport.build(BillHistory.build(List.of(HISTORY.first())));
        assertThat(single.steps()).isEmpty();
        assertThat(single.render())
                .isEqualTo("bill revisions\n==============\n\nsteps                    0\n"
                        + "steps that moved a rate  0");
    }
}
