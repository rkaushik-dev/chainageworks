package io.github.rkaushikdev.chainageworks.domain.quantity;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The units a civil bill of quantities is written in: the CESMM set plus the two time units that
 * turn up in dayworks, and nothing else. A unit the bill cannot be written in is a unit nothing can
 * be claimed against.
 */
public enum Unit {
    M("m", "linear metre", Dimension.LENGTH, 2, true),
    M2("m2", "square metre", Dimension.AREA, 2, true),
    M3("m3", "cubic metre", Dimension.VOLUME, 2, true),
    HA("ha", "hectare", Dimension.AREA, 3, true),
    NR("nr", "number", Dimension.COUNT, 0, true),
    T("t", "tonne", Dimension.MASS, 3, true),
    KG("kg", "kilogram", Dimension.MASS, 1, true),
    HR("hr", "hour", Dimension.TIME, 2, true),
    WK("wk", "week", Dimension.TIME, 2, true),
    /**
     * A lump sum. Nothing measures a {@code sum} item: it is earned through a rule of credit or
     * not at all, and treating it as a quantity would let a survey book half of it.
     */
    SUM("sum", "lump sum", Dimension.LUMP, 0, false);

    /** Every unit, in the order the bill lists them. */
    public static final List<Unit> ALL = List.of(values());

    private static final Pattern SPACE_RUN = Patterns.compile("\\s+");

    /** What site paperwork actually writes, keyed to what the bill means. */
    private static final Map<String, Unit> ALIASES = Map.ofEntries(
            Map.entry("lm", M),
            Map.entry("lin m", M),
            Map.entry("lin.m", M),
            Map.entry("metre", M),
            Map.entry("meter", M),
            Map.entry("m2", M2),
            Map.entry("m²", M2),
            Map.entry("sqm", M2),
            Map.entry("sq m", M2),
            Map.entry("sm", M2),
            Map.entry("m3", M3),
            Map.entry("m³", M3),
            Map.entry("cum", M3),
            Map.entry("cu m", M3),
            Map.entry("cm", M3),
            Map.entry("no", NR),
            Map.entry("no.", NR),
            Map.entry("num", NR),
            Map.entry("each", NR),
            Map.entry("ea", NR),
            Map.entry("item", NR),
            Map.entry("te", T),
            Map.entry("tonne", T),
            Map.entry("tonnes", T),
            Map.entry("ton", T),
            Map.entry("hour", HR),
            Map.entry("hours", HR),
            Map.entry("h", HR),
            Map.entry("week", WK),
            Map.entry("weeks", WK),
            Map.entry("ls", SUM),
            Map.entry("lump sum", SUM),
            Map.entry("hectare", HA));

    private final String code;
    private final String unitName;
    private final Dimension dimension;
    private final int decimals;
    private final boolean measurable;

    Unit(String code, String unitName, Dimension dimension, int decimals, boolean measurable) {
        this.code = code;
        this.unitName = unitName;
        this.dimension = dimension;
        this.decimals = decimals;
        this.measurable = measurable;
    }

    /** The code the bill writes: {@code m3}, {@code nr}, {@code sum}. */
    public String code() {
        return code;
    }

    /** The unit in full: {@code cubic metre}. */
    public String unitName() {
        return unitName;
    }

    public Dimension dimension() {
        return dimension;
    }

    /** Decimal places a measured quantity is billed to. */
    public int decimals() {
        return decimals;
    }

    /** False for a lump sum, the one unit nothing measures. */
    public boolean isMeasurable() {
        return measurable;
    }

    @Override
    public String toString() {
        return code;
    }

    /** True for exactly one of the canonical codes, as the bill writes them. */
    public static boolean isCode(String value) {
        return fromCode(value).isPresent();
    }

    /** The unit for an exact canonical code. */
    public static Optional<Unit> fromCode(String value) {
        for (Unit unit : values()) {
            if (unit.code.equals(value)) {
                return Optional.of(unit);
            }
        }
        return Optional.empty();
    }

    /** Case and punctuation insensitive, because nobody types {@code m3} on a site sheet. */
    public static Unit parse(String text) {
        String cleaned = SPACE_RUN.matcher(Text.lower(Text.trim(text))).replaceAll(" ");
        if (cleaned.isEmpty()) {
            throw new BadInputException("unit is blank");
        }
        Optional<Unit> canonical = fromCode(cleaned);
        if (canonical.isPresent()) {
            return canonical.get();
        }
        Unit alias = ALIASES.get(cleaned);
        if (alias != null) {
            return alias;
        }
        throw new BadInputException("unknown unit", Details.of("text", Text.trim(text)));
    }

    /**
     * Two units may be added only when they are the same unit. Same dimension is not enough: a
     * tonne and a kilogram both weigh things, and silently converting one to the other would hide
     * a bill written against the wrong item.
     */
    public static void assertSame(Unit a, Unit b) {
        if (a != b) {
            throw new BadInputException(
                    "cannot combine quantities in different units", Details.of("left", a.code, "right", b.code));
        }
    }
}
