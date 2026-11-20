package io.github.rkaushikdev.chainageworks.domain.money;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.domain.quantity.Quantity;
import io.github.rkaushikdev.chainageworks.util.Numbers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Split a total across weighted shares so the shares add back to the total exactly.
 *
 * <p>The naive way, rounding each share on its own, loses or gains pennies: a retention release
 * split three ways is a penny short about a third of the time, and on a valuation that penny has to
 * be chased. This uses largest remainder, so the leftover pennies go to the shares with the biggest
 * fractional part and the sum is right by construction.
 */
public final class Apportion {

    private Apportion() {}

    private record Fraction(int index, double fraction) {}

    /** Split a whole number of units across {@code weights}. */
    public static List<Long> units(double total, List<? extends Number> weights) {
        if (!Numbers.isInteger(total)) {
            throw new BadInputException("can only apportion a whole number", Details.of("total", total));
        }
        if (weights.isEmpty()) {
            if (total != 0) {
                throw new BadInputException("nothing to apportion a total across", Details.of("total", total));
            }
            return List.of();
        }
        double weightTotal = 0;
        for (int index = 0; index < weights.size(); index++) {
            double weight = weights.get(index).doubleValue();
            if (!Double.isFinite(weight) || weight < 0) {
                throw new BadInputException(
                        "apportionment weights must be finite and not negative",
                        Details.of("index", index, "weight", Numbers.format(weight)));
            }
            weightTotal += weight;
        }
        if (weightTotal == 0) {
            if (total != 0) {
                throw new BadInputException(
                        "cannot apportion a total across weights that are all zero", Details.of("total", total));
            }
            return Collections.nCopies(weights.size(), 0L);
        }

        List<Long> shares = new ArrayList<>(weights.size());
        List<Fraction> fractions = new ArrayList<>(weights.size());
        double allocated = 0;
        for (int index = 0; index < weights.size(); index++) {
            double exact = (total * weights.get(index).doubleValue()) / weightTotal;
            double floor = Math.floor(exact);
            shares.add((long) floor);
            fractions.add(new Fraction(index, exact - floor));
            allocated += floor;
        }

        // Ties go to the earlier share, so the same input always gives the same split.
        fractions.sort(Comparator.comparingDouble(Fraction::fraction).reversed().thenComparingInt(Fraction::index));
        double remainder = total - allocated;
        for (Fraction entry : fractions) {
            if (remainder == 0) {
                break;
            }
            shares.set(entry.index(), shares.get(entry.index()) + 1);
            remainder -= 1;
        }
        return List.copyOf(shares);
    }

    /** Split money across {@code weights}, to the penny. */
    public static List<Money> money(Money total, List<? extends Number> weights) {
        return units(total.minor(), weights).stream()
                .map(minor -> new Money(minor, total.currency()))
                .toList();
    }

    /** Split a quantity across {@code weights}, to the thousandth. */
    public static List<Quantity> quantity(Quantity total, List<? extends Number> weights) {
        return units(total.milli(), weights).stream()
                .map(milli -> new Quantity(milli, total.unit()))
                .toList();
    }

    /** Weight the split by measured quantities, which is the usual case. */
    public static List<Money> moneyByQuantity(Money total, List<Quantity> quantities) {
        return money(total, quantities.stream().map(Quantity::milli).toList());
    }

    /** Equal shares, with the odd pennies going to the earliest shares. */
    public static List<Money> splitEvenly(Money total, int parts) {
        if (parts < 1) {
            throw new BadInputException("need at least one share to split into", Details.of("parts", parts));
        }
        return money(total, Collections.nCopies(parts, 1));
    }

    /**
     * Check that a set of shares really does add back to the total. Used by the valuation
     * assembler, which builds shares from several sources and has no other way to know they still
     * balance.
     */
    public static boolean sharesBalance(Money total, List<Money> shares) {
        long sum = 0;
        for (Money share : shares) {
            if (share.currency() != total.currency()) {
                return false;
            }
            sum += share.minor();
        }
        return sum == total.minor();
    }
}
