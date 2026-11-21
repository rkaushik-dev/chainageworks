package io.github.rkaushikdev.chainageworks.cli;

import io.github.rkaushikdev.chainageworks.util.Collation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A guess at the command or option that was meant, for the line that says one was not recognised.
 *
 * <p>The guess is every candidate at the smallest edit distance from what was typed, where a swap of
 * two neighbouring characters counts as one edit, provided that distance is under three and the two
 * words share more than two fifths of their length. Anything further off is not offered: a guess that
 * is usually wrong is worse than none. Nor is the word itself, or a candidate of a single character.
 */
final class Suggestion {

    private static final int MOST_EDITS = 3;
    private static final double LEAST_SIMILARITY = 0.4;

    private Suggestion() {}

    /**
     * The guess as it follows the complaint: {@code \n(Did you mean certificate?)}, {@code \n(Did you
     * mean one of a, b?)}, or nothing. A word written {@code --name} is compared without its dashes.
     */
    static String of(String typed, List<String> candidates) {
        boolean option = typed.startsWith("--");
        String word = option ? typed.substring(2) : typed;
        List<String> similar = new ArrayList<>();
        int best = MOST_EDITS;
        for (String candidate : new LinkedHashSet<>(candidates)) {
            String name = option ? candidate.substring(2) : candidate;
            if (name.length() <= 1 || name.equals(word)) {
                continue;
            }
            int distance = distance(word, name);
            int length = Math.max(word.length(), name.length());
            double similarity = (length - distance) / (double) length;
            if (similarity <= LEAST_SIMILARITY) {
                continue;
            }
            if (distance < best) {
                best = distance;
                similar.clear();
                similar.add(name);
            } else if (distance == best) {
                similar.add(name);
            }
        }
        similar.sort(Collation.ORDER);
        List<String> shown =
                similar.stream().map(name -> option ? "--" + name : name).toList();
        if (shown.size() > 1) {
            return "\n(Did you mean one of " + String.join(", ", shown) + "?)";
        }
        return shown.isEmpty() ? "" : "\n(Did you mean " + shown.getFirst() + "?)";
    }

    /**
     * The edits that turn {@code a} into {@code b}, counting a swap of neighbours as one and editing no
     * part twice. Words whose lengths differ by more than the most edits worth guessing at are not
     * worked out: the longer length is answered.
     */
    static int distance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > MOST_EDITS) {
            return Math.max(a.length(), b.length());
        }
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int j = 1; j <= b.length(); j++) {
            for (int i = 1; i <= a.length(); i++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
