package ar.rindetuplata.source.wallet;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stable key of a fund class. ArgentinaDatos publishes no class id (L7), so a class is identified by its display
 * name; the key makes the match survive harmless edits of that name: case, accents and runs of whitespace.
 */
public final class FundClassKey {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Greedy prefix, so the cut is at the last " - clase " of an already normalized key. */
    private static final Pattern CLASS_SUFFIX = Pattern.compile("^(.*) - clase(?: .*)?$");

    private FundClassKey() {
    }

    /** Lower case, accents stripped, whitespace collapsed to one space and trimmed. */
    public static String of(String className) {
        String decomposed = Normalizer.normalize(className, Normalizer.Form.NFD);
        String unaccented = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(unaccented.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /**
     * The key of the fund a class belongs to: the key of the text before the last " - Clase " (case and accents do
     * not matter). A name without that suffix is its own fund.
     */
    public static String fundOf(String className) {
        String key = of(className);
        Matcher suffix = CLASS_SUFFIX.matcher(key);
        return suffix.matches() ? suffix.group(1).trim() : key;
    }
}
