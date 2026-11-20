package io.github.rkaushikdev.chainageworks.domain.credit;

import static io.github.rkaushikdev.chainageworks.domain.credit.CreditStep.afterPrevious;
import static io.github.rkaushikdev.chainageworks.domain.credit.CreditStep.of;

import io.github.rkaushikdev.chainageworks.domain.money.Percent;
import java.util.List;
import java.util.Map;

/**
 * The rules of credit that come with the tool.
 *
 * <p>These are the ones a civil job uses over and over. A contract can define its own and register
 * them instead, but starting from an empty catalogue means every job spends its first week writing
 * out the same five profiles.
 */
public final class CreditCatalogue {

    private CreditCatalogue() {}

    private static Percent p(double value) {
        return Percent.of(value);
    }

    public static final CreditProfile SINGLE_STEP =
            CreditProfile.build("single-step", "complete or not", List.of(of("complete", "complete", p(100))));

    public static final CreditProfile PIPE_RUN = CreditProfile.build(
            "pipe-run",
            "pipe laying",
            List.of(
                    of("excavate", "excavate trench", p(25)),
                    afterPrevious("bed", "lay bedding", p(10)),
                    afterPrevious("lay", "lay and joint pipe", p(30)),
                    afterPrevious("backfill", "backfill and compact", p(25)),
                    afterPrevious("test", "pressure test", p(10))));

    public static final CreditProfile BULK_EARTHWORKS = CreditProfile.build(
            "bulk-earthworks",
            "bulk earthworks",
            List.of(
                    of("strip", "strip topsoil", p(10)),
                    of("excavate", "excavate to formation", p(45)),
                    afterPrevious("place", "place and compact", p(35)),
                    afterPrevious("trim", "trim to level", p(10))));

    public static final CreditProfile STRUCTURAL_CONCRETE = CreditProfile.build(
            "structural-concrete",
            "in situ concrete",
            List.of(
                    of("formwork", "erect formwork", p(30)),
                    of("reinforcement", "fix reinforcement", p(25)),
                    afterPrevious("pour", "pour concrete", p(35)),
                    afterPrevious("strike", "strike and cure", p(10))));

    public static final CreditProfile PAVEMENT = CreditProfile.build(
            "pavement",
            "flexible pavement",
            List.of(
                    of("subbase", "lay sub base", p(25)),
                    afterPrevious("base", "lay base course", p(35)),
                    afterPrevious("binder", "lay binder course", p(25)),
                    afterPrevious("surface", "lay surface course", p(15))));

    public static final CreditProfile DRAINAGE_STRUCTURE = CreditProfile.build(
            "drainage-structure",
            "manhole or chamber",
            List.of(
                    of("base", "cast base slab", p(20)),
                    afterPrevious("chamber", "build chamber", p(40)),
                    afterPrevious("slab", "cover slab", p(20)),
                    afterPrevious("ironwork", "set cover and frame", p(20))));

    public static final CreditProfile SITE_SETUP = CreditProfile.build(
            "site-setup",
            "establish and remove",
            List.of(
                    of("mobilise", "mobilise", p(60)),
                    of("maintain", "maintain for the duration", p(30)),
                    afterPrevious("demobilise", "demobilise", p(10))));

    /** The seven a civil job keeps needing, in catalogue order. */
    public static final List<CreditProfile> STANDARD_PROFILES = List.of(
            SINGLE_STEP, PIPE_RUN, BULK_EARTHWORKS, STRUCTURAL_CONCRETE, PAVEMENT, DRAINAGE_STRUCTURE, SITE_SETUP);

    /** Which profile a work class falls back to when the package does not say. */
    static final Map<String, String> BY_WORK_CLASS = Map.ofEntries(
            Map.entry("A", "site-setup"),
            Map.entry("D", "single-step"),
            Map.entry("E", "bulk-earthworks"),
            Map.entry("F", "structural-concrete"),
            Map.entry("G", "structural-concrete"),
            Map.entry("H", "structural-concrete"),
            Map.entry("I", "pipe-run"),
            Map.entry("J", "pipe-run"),
            Map.entry("K", "drainage-structure"),
            Map.entry("R", "pavement"),
            Map.entry("U", "structural-concrete"));
}
