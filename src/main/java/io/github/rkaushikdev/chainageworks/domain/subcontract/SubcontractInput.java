package io.github.rkaushikdev.chainageworks.domain.subcontract;

import io.github.rkaushikdev.chainageworks.domain.bill.ItemCode;
import io.github.rkaushikdev.chainageworks.domain.money.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * What a subcontract is built from. {@code null} lists mean none and {@code null} terms mean the
 * statutory defaults.
 *
 * @param id the order's key
 * @param name what the order is for
 * @param contractor the subcontractor
 * @param orderValue the order value
 * @param packageIds the packages the subcontractor works in
 * @param codes the bill items it is paid against
 * @param terms the payment terms
 * @param placedOn the day the order was placed
 */
public record SubcontractInput(
        String id,
        String name,
        String contractor,
        Money orderValue,
        List<String> packageIds,
        List<ItemCode> codes,
        PaymentTerms terms,
        LocalDate placedOn) {

    public SubcontractInput {
        packageIds = packageIds == null ? List.of() : List.copyOf(packageIds);
        codes = codes == null ? List.of() : List.copyOf(codes);
    }

    /** An order with no packages, no items and the statutory terms. */
    public static SubcontractInput of(String id, String name, String contractor, Money orderValue, LocalDate placedOn) {
        return new SubcontractInput(id, name, contractor, orderValue, List.of(), List.of(), null, placedOn);
    }

    public SubcontractInput withPackages(List<String> other) {
        return new SubcontractInput(id, name, contractor, orderValue, other, codes, terms, placedOn);
    }

    public SubcontractInput withCodes(List<ItemCode> other) {
        return new SubcontractInput(id, name, contractor, orderValue, packageIds, other, terms, placedOn);
    }

    public SubcontractInput withTerms(PaymentTerms other) {
        return new SubcontractInput(id, name, contractor, orderValue, packageIds, codes, other, placedOn);
    }
}
