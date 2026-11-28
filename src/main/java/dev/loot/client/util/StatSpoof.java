package dev.loot.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds stat values in text ("Money: $1.2M", "Shards 540", "Playtime 3d 4h") and swaps the number
 * for another one. Works on styled segments (one per color run) so a line whose label and value
 * are different colors still matches, and the colors stay as they were. Pure Java, unit tested.
 */
public final class StatSpoof {
    private StatSpoof() {}

    /** One stat: the words that label it, the value to show, and whether the value is a duration. */
    public record Rule(List<String> labels, String value, boolean time) {
        public Rule {
            labels = labels.stream().map(l -> l.toLowerCase(Locale.ROOT)).toList();
            value = value.trim();
        }
    }

    public record Seg<S>(String text, S style) {}

    private static final Pattern NUMBER = Pattern.compile("^\\d[\\d,]*(?:\\.\\d+)?(?:[kKmMbBtTqQ](?![a-zA-Z]))?");
    private static final Pattern TIME = Pattern.compile("^\\d+\\s?[dhmsDHMS](?![a-zA-Z])(?:\\s?\\d+\\s?[dhmsDHMS](?![a-zA-Z]))*");
    /** What may sit between a label and its value: spaces, colons, arrows, bars, dots, a dollar sign. */
    private static final String SEPARATORS = " \t:»>|-=•·$⛃⛂✦❖◆*";
    private static final int MAX_GAP = 6;

    /** Replaces stat values in a plain string. Returns the same instance if nothing matched. */
    public static String apply(String text, List<Rule> rules) {
        List<Seg<Object>> segs = apply(List.of(new Seg<>(text, null)), rules);
        return segs == null ? text : segs.get(0).text();
    }

    /** Replaces stat values across styled segments. Returns null if nothing matched. */
    public static <S> List<Seg<S>> apply(List<Seg<S>> segs, List<Rule> rules) {
        if (rules.isEmpty() || segs.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Seg<S> s : segs) sb.append(s.text());
        String flat = sb.toString();
        if (flat.isEmpty()) return null;
        String lower = flat.toLowerCase(Locale.ROOT);

        List<int[]> spans = new ArrayList<>(); // start, end, rule index
        for (int r = 0; r < rules.size(); r++) {
            Rule rule = rules.get(r);
            if (rule.value().isEmpty()) continue;
            for (String label : rule.labels()) {
                int from = 0, at;
                while ((at = lower.indexOf(label, from)) >= 0) {
                    from = at + label.length();
                    // whole words only: "bal" shouldn't fire inside "global"
                    if (Character.isLetter(label.charAt(0)) && at > 0 && Character.isLetter(flat.charAt(at - 1))) continue;
                    int end = at + label.length();
                    if (Character.isLetter(label.charAt(label.length() - 1)) && end < flat.length() && Character.isLetter(flat.charAt(end))) continue;
                    int v = end, gap = 0;
                    while (v < flat.length() && gap < MAX_GAP && SEPARATORS.indexOf(flat.charAt(v)) >= 0) {
                        v++;
                        gap++;
                    }
                    Matcher m = (rule.time() ? TIME : NUMBER).matcher(flat.substring(v));
                    if (!m.find()) continue;
                    spans.add(new int[]{v, v + m.end(), r});
                }
            }
        }
        if (spans.isEmpty()) return null;
        spans.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));

        List<Seg<S>> out = new ArrayList<>(segs);
        int shift = 0, lastEnd = -1;
        boolean changed = false;
        for (int[] sp : spans) {
            if (sp[0] < lastEnd) continue; // overlaps one already replaced ("Balance $5" matched by both words)
            lastEnd = sp[1];
            String value = rules.get(sp[2]).value();
            if (value.startsWith("$") && sp[0] > 0 && flat.charAt(sp[0] - 1) == '$') value = value.substring(1);
            String old = flat.substring(sp[0], sp[1]);
            if (old.equals(value)) continue;
            out = replace(out, sp[0] + shift, sp[1] + shift, value);
            shift += value.length() - (sp[1] - sp[0]);
            changed = true;
        }
        return changed ? out : null;
    }

    /** Replaces flat range [start, end) with value, keeping the style of the segment where it starts. */
    private static <S> List<Seg<S>> replace(List<Seg<S>> segs, int start, int end, String value) {
        List<Seg<S>> out = new ArrayList<>(segs.size());
        int pos = 0;
        boolean placed = false;
        for (Seg<S> s : segs) {
            int a = pos, b = pos + s.text().length();
            pos = b;
            if (b <= start || a >= end) {
                // untouched, but a zero-length segment exactly at start still gets nothing
                out.add(s);
                continue;
            }
            String t = s.text();
            int cutFrom = Math.max(start, a) - a, cutTo = Math.min(end, b) - a;
            String keep = t.substring(0, cutFrom) + (placed ? "" : value) + t.substring(cutTo);
            placed = true;
            if (!keep.isEmpty()) out.add(new Seg<>(keep, s.style()));
        }
        return out;
    }

    /** "Kills=500, Keys=12" into rules, one label each. */
    public static List<Rule> parseCustom(String spec) {
        List<Rule> out = new ArrayList<>();
        if (spec == null) return out;
        for (String part : spec.split("[,;]")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String label = part.substring(0, eq).trim(), value = part.substring(eq + 1).trim();
            if (label.isEmpty() || value.isEmpty()) continue;
            out.add(new Rule(List.of(label), value, false));
        }
        return out;
    }
}
