package ar.rindetuplata.source.wallet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Extracts the text snippets that announce a TNA rate from an HTML page, with the same rules as
 * {@code capture/extract_rates.py}: script/style/noscript/template/svg and comments are ignored, block-level
 * elements split the text, inline ones do not, and unique snippets keep page order.
 */
final class HtmlRateSnippets {

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SKIPPED = Pattern.compile(
            "<(script|style|noscript|template|svg)\\b[^>]*>.*?</\\1\\s*>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_TAG = Pattern.compile(
            "</?(?:address|article|aside|blockquote|br|dd|div|dl|dt|footer|form|h1|h2|h3|h4|h5|h6|header|hr|li|main|nav"
                    + "|ol|p|section|table|td|th|tr|ul)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0]+");
    private static final Pattern RATE = Pattern.compile("\\d{1,3}(?:[.,]\\d+)?\\s*%\\s*TNA", Pattern.CASE_INSENSITIVE);

    private HtmlRateSnippets() {
    }

    static List<String> extract(String html) {
        String text = COMMENT.matcher(html).replaceAll("");
        text = SKIPPED.matcher(text).replaceAll("");
        text = BLOCK_TAG.matcher(text).replaceAll("\n");
        text = ANY_TAG.matcher(text).replaceAll("");
        text = HtmlUtils.htmlUnescape(text);
        LinkedHashSet<String> snippets = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            String snippet = WHITESPACE.matcher(line).replaceAll(" ").trim();
            if (!snippet.isEmpty() && RATE.matcher(snippet).find()) {
                snippets.add(snippet);
            }
        }
        return List.copyOf(snippets);
    }
}
