package io.github.rkaushikdev.chainageworks.domain.bill;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.CwException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.Result;
import io.github.rkaushikdev.chainageworks.util.Patterns;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A bill item code: a CESMM class letter, up to three division digits, and the contractor's own
 * numbering after a dot ({@code E423.1}).
 *
 * <p>The class letter is not decoration: it is what decides which measurement rules apply to an
 * item, so it has to survive parsing rather than being flattened into the description.
 *
 * @param workClass the upper case class letter
 * @param divisions the division digits, at most three
 * @param suffix the upper case numbering after the dot, if any
 */
public record ItemCode(String workClass, List<Integer> divisions, Optional<String> suffix)
        implements Comparable<ItemCode> {

    /** The CESMM work classes, in letter order. */
    public static final Map<String, String> WORK_CLASSES = workClasses();

    public static final int MAX_DIVISIONS = 3;

    /**
     * Bill order: class, then division by division, then the suffix. A shorter code sorts before a
     * longer one that starts the same way, so a heading always comes before the items under it.
     */
    public static final Comparator<ItemCode> BILL_ORDER = ItemCode::compare;

    private static final Pattern CODE_TEXT =
            Patterns.compile("^([A-Za-z])\\s*((?:\\s*\\d){0,3})\\s*(?:\\.\\s*([A-Za-z0-9]+))?$");
    private static final Pattern SUFFIX_TEXT = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SPACE_RUN = Patterns.compile("\\s+");

    public ItemCode {
        divisions = List.copyOf(divisions);
        suffix = suffix == null ? Optional.empty() : suffix;
    }

    private static Map<String, String> workClasses() {
        Map<String, String> classes = new LinkedHashMap<>();
        classes.put("A", "general items");
        classes.put("B", "ground investigation");
        classes.put("C", "geotechnical and other specialist processes");
        classes.put("D", "demolition and site clearance");
        classes.put("E", "earthworks");
        classes.put("F", "in situ concrete");
        classes.put("G", "concrete ancillaries");
        classes.put("H", "precast concrete");
        classes.put("I", "pipework, pipes");
        classes.put("J", "pipework, fittings and valves");
        classes.put("K", "pipework, manholes and ancillaries");
        classes.put("L", "pipework, supports and protection");
        classes.put("M", "structural metalwork");
        classes.put("N", "miscellaneous metalwork");
        classes.put("O", "timber");
        classes.put("P", "piles");
        classes.put("Q", "piling ancillaries");
        classes.put("R", "roads and pavings");
        classes.put("S", "rail track");
        classes.put("T", "tunnels");
        classes.put("U", "brickwork, blockwork and masonry");
        classes.put("V", "painting");
        classes.put("W", "waterproofing");
        classes.put("X", "miscellaneous work");
        classes.put("Y", "sewer and water main renovation");
        classes.put("Z", "simple building works");
        return Collections.unmodifiableMap(classes);
    }

    /** A bare class letter. */
    public static ItemCode of(String workClass) {
        return of(workClass, List.of(), null);
    }

    /** A class letter and divisions, with no suffix. */
    public static ItemCode of(String workClass, List<Integer> divisions) {
        return of(workClass, divisions, null);
    }

    /**
     * A code from its parts, refusing a letter that is not a class, more than three divisions, a
     * division that is not one digit, and a suffix with punctuation in it.
     *
     * @param suffix the numbering after the dot, or {@code null} for none
     */
    public static ItemCode of(String workClass, List<Integer> divisions, String suffix) {
        String upper = Text.upper(workClass);
        if (!WORK_CLASSES.containsKey(upper)) {
            throw new BadInputException("not a work class letter", Details.of("workClass", workClass));
        }
        if (divisions.size() > MAX_DIVISIONS) {
            throw new BadInputException(
                    "an item code carries at most three divisions", Details.of("divisions", divisions.size()));
        }
        for (Integer division : divisions) {
            if (division == null || division < 0 || division > 9) {
                throw new BadInputException("a division is a single digit", Details.of("division", division));
            }
        }
        if (suffix != null && !SUFFIX_TEXT.matcher(suffix).matches()) {
            throw new BadInputException("a code suffix is letters and digits only", Details.of("suffix", suffix));
        }
        return new ItemCode(upper, divisions, Optional.ofNullable(suffix).map(Text::upper));
    }

    /** Read {@code E423.1}, or the spaced form off a bill page, {@code E 4 2 3 . 1}. */
    public static Result<ItemCode, CwException> tryParse(String text) {
        String trimmed = Text.trim(text);
        Matcher m = CODE_TEXT.matcher(trimmed);
        if (!m.find()) {
            return Result.err(new BadInputException("not a bill item code", Details.of("text", trimmed)));
        }
        String digits = SPACE_RUN.matcher(m.group(2) == null ? "" : m.group(2)).replaceAll("");
        List<Integer> divisions = new ArrayList<>();
        for (char c : digits.toCharArray()) {
            divisions.add(c - '0');
        }
        try {
            return Result.ok(of(m.group(1), divisions, m.group(3)));
        } catch (BadInputException refused) {
            return Result.err(refused);
        }
    }

    /** {@link #tryParse} that throws its failure. */
    public static ItemCode parse(String text) {
        return Result.unwrap(tryParse(text));
    }

    /** {@code E423.1}. */
    public String format() {
        return format(false);
    }

    /** {@code E423.1}, or {@code E 4 2 3.1} when {@code spaced}. */
    public String format(boolean spaced) {
        String digits = divisions.stream().map(String::valueOf).collect(Collectors.joining(spaced ? " " : ""));
        String head = spaced && !digits.isEmpty() ? workClass + " " + digits : workClass + digits;
        return suffix.map(s -> head + "." + s).orElse(head);
    }

    @Override
    public String toString() {
        return format();
    }

    /** The name of the work class: {@code earthworks} for E. */
    public String workClassName() {
        return WORK_CLASSES.getOrDefault(workClass, "unknown");
    }

    public int depth() {
        return divisions.size();
    }

    /** The code one level up: the suffix goes first, then a division at a time. */
    public Optional<ItemCode> parent() {
        if (suffix.isPresent()) {
            return Optional.of(new ItemCode(workClass, divisions, Optional.empty()));
        }
        if (divisions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ItemCode(workClass, divisions.subList(0, divisions.size() - 1), Optional.empty()));
    }

    /**
     * Whether this code is a heading {@code code} sits under. A code is not its own ancestor,
     * which is what the bill rollup relies on to avoid counting a heading's own value twice.
     */
    public boolean isAncestorOf(ItemCode code) {
        if (!workClass.equals(code.workClass)) {
            return false;
        }
        if (suffix.isPresent()) {
            return false;
        }
        if (divisions.size() > code.divisions.size()) {
            return false;
        }
        if (divisions.size() == code.divisions.size() && code.suffix.isEmpty()) {
            return false;
        }
        for (int i = 0; i < divisions.size(); i++) {
            if (!divisions.get(i).equals(code.divisions.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(ItemCode other) {
        return compare(this, other);
    }

    private static int compare(ItemCode a, ItemCode b) {
        if (!a.workClass.equals(b.workClass)) {
            return a.workClass.compareTo(b.workClass) < 0 ? -1 : 1;
        }
        int shared = Math.min(a.divisions.size(), b.divisions.size());
        for (int i = 0; i < shared; i += 1) {
            int left = a.divisions.get(i);
            int right = b.divisions.get(i);
            if (left != right) {
                return left - right;
            }
        }
        if (a.divisions.size() != b.divisions.size()) {
            return a.divisions.size() - b.divisions.size();
        }
        if (a.suffix.equals(b.suffix)) {
            return 0;
        }
        if (a.suffix.isEmpty()) {
            return -1;
        }
        if (b.suffix.isEmpty()) {
            return 1;
        }
        return compareSuffixes(a.suffix.get(), b.suffix.get());
    }

    /** {@code 2} sorts before {@code 10}, which a plain string comparison gets wrong. */
    private static int compareSuffixes(String a, String b) {
        boolean aNumeric = DIGITS.matcher(a).matches();
        boolean bNumeric = DIGITS.matcher(b).matches();
        if (aNumeric && bNumeric) {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        }
        if (aNumeric) {
            return -1;
        }
        if (bNumeric) {
            return 1;
        }
        return a.compareTo(b) < 0 ? -1 : 1;
    }

    /** A stable string key for maps and for sorting outside this type: {@code E4__.} for E4. */
    public String key() {
        String digits = divisions.stream().map(String::valueOf).collect(Collectors.joining());
        return workClass + Text.padEnd(digits, MAX_DIVISIONS, '_') + "." + suffix.orElse("");
    }
}
