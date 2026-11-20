package io.github.rkaushikdev.chainageworks.domain.credit;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;

/**
 * One step in a rule of credit.
 *
 * <p>A rule of credit is how a measured item earns before it is finished. Nobody pays for a pipe
 * run when the trench is open and nobody waits for the whole run before paying anything, so the
 * value is split across the steps that can actually be seen on site.
 *
 * @param id the step's key inside its profile
 * @param name what the step is called on site
 * @param weight share of the item's value this step is worth
 * @param requiresPrevious whether the step is worth nothing until every step before it is
 *     finished. This is what stops a lazy claim: backfill cannot be credited while the pipe is
 *     unlaid even though the trench beside it has been backfilled
 */
public record CreditStep(String id, String name, Percent weight, boolean requiresPrevious) {

    /** A step that earns whatever the steps before it have done. */
    public static CreditStep of(String id, String name, Percent weight) {
        return new CreditStep(id, name, weight, false);
    }

    /** A step that earns nothing until every step before it is finished. */
    public static CreditStep afterPrevious(String id, String name, Percent weight) {
        return new CreditStep(id, name, weight, true);
    }
}
