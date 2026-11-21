package io.github.rkaushikdev.chainageworks.valuation;

import io.github.rkaushikdev.chainageworks.domain.money.Currency;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.util.List;

/**
 * What a cash forecast is worked out from.
 *
 * @param periods the valuation periods, in order
 * @param certified net certified to date at the end of each period, in period order
 * @param currency what the forecast is counted in
 * @param latePeriods how many periods after falling due each certificate is paid; null means on time
 */
public record CashFlowInput(
        List<ValuationPeriod> periods, List<Money> certified, Currency currency, Integer latePeriods) {

    public CashFlowInput {
        periods = List.copyOf(periods);
        certified = List.copyOf(certified);
    }

    /** A forecast where every certificate is paid on its due date. */
    public static CashFlowInput of(List<ValuationPeriod> periods, List<Money> certified, Currency currency) {
        return new CashFlowInput(periods, certified, currency, null);
    }

    public CashFlowInput withLatePeriods(int other) {
        return new CashFlowInput(periods, certified, currency, other);
    }
}
