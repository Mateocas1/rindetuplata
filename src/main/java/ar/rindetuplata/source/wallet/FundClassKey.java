package ar.rindetuplata.source.wallet;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The stable key of a fund class. ArgentinaDatos publishes no class id (L7), so a class is identified by its display
 * name; the key makes the match survive harmless edits of that name: case, accents and runs of whitespace.
 */
public final class FundClassKey {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private FundClassKey() {
    }

    /** Lower case, accents stripped, whitespace collapsed to one space and trimmed. */
    public static String of(String className) {
        String decomposed = Normalizer.normalize(className, Normalizer.Form.NFD);
        String unaccented = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(unaccented.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
