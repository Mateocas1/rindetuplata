package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceHttp;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.Units;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Ualá remunerated account, scraped from the entity web. Reads the live page ({@link #parseHtml}) or the T9
 * {@code html-rate} extract ({@link #parseExtract}); both go through the same snippet rules. The unconditional rate
 * is the "entre $X y $Y ... N% TNA" snippet; "Si ... sube hasta N% TNA" snippets become labeled conditions;
 * headline snippets ("hasta 24%") are ignored.
 */
public class UalaWalletAdapter implements WalletRateSource {

    public static final SourceRef SOURCE = new SourceRef("uala-cuenta-remunerada", "Ualá", false);

    private static final String WALLET_ID = "uala";
    private static final String WALLET_NAME = "Ualá";
    private static final Pattern BASE = Pattern.compile(
            "entre \\$([\\d.]+) y \\$([\\d.]+).*?(\\d{1,3}(?:[.,]\\d+)?)\\s*%\\s*TNA", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITIONAL = Pattern.compile(
            "^(Si .+?)\\s*,?\\s*(?:tu tasa\\s+)?sube hasta (\\d{1,3}(?:[.,]\\d+)?)\\s*%\\s*TNA", Pattern.CASE_INSENSITIVE);

    private final RestClient client;
    private final Clock clock;

    public UalaWalletAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<WalletRate> fetch() {
        Instant fetchedAt = clock.instant();
        return List.of(parseHtml(SourceHttp.get(client, SOURCE, "/inversiones/cuenta-remunerada"), fetchedAt));
    }

    /** Pure: live page bytes → wallet rate. */
    public static WalletRate parseHtml(byte[] html, Instant fetchedAt) {
        return fromSnippets(HtmlRateSnippets.extract(new String(html, StandardCharsets.UTF_8)), fetchedAt);
    }

    /** Pure: T9 archive extract ({@code {"matches": [...]}}) → wallet rate. */
    public static WalletRate parseExtract(byte[] extract, Instant fetchedAt) {
        JsonNode matches = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, extract), "matches");
        List<String> snippets = new ArrayList<>();
        matches.forEach(m -> snippets.add(m.asString()));
        return fromSnippets(snippets, fetchedAt);
    }

    private static WalletRate fromSnippets(List<String> snippets, Instant fetchedAt) {
        WalletTier base = null;
        List<WalletCondition> conditions = new ArrayList<>();
        for (String snippet : snippets) {
            Matcher baseMatch = BASE.matcher(snippet);
            Matcher conditional = CONDITIONAL.matcher(snippet);
            if (base == null && baseMatch.find()) {
                base = new WalletTier(money(baseMatch.group(1)), money(baseMatch.group(2)), percent(baseMatch.group(3)));
            } else if (conditional.find()) {
                conditions.add(new WalletCondition(conditional.group(1),
                        List.of(new WalletTier(null, null, percent(conditional.group(2))))));
            }
        }
        if (base == null) {
            throw new SourceException(SOURCE, "no base rate snippet found; the page layout may have changed", null);
        }
        List<ValidationFlag> flags = new ArrayList<>(ValidationRules.rateFlags(base.tna()));
        conditions.forEach(c -> c.tiers().forEach(t -> flags.addAll(ValidationRules.rateFlags(t.tna()))));
        return new WalletRate(SOURCE, WALLET_ID, WALLET_NAME, List.of(base), conditions,
                ValidationRules.localDate(fetchedAt), null, fetchedAt, Confidence.SCRAPED_ENTITY_WEB, flags);
    }

    /** Argentine amounts use "." for thousands. */
    private static BigDecimal money(String amount) {
        return new BigDecimal(amount.replace(".", ""));
    }

    private static BigDecimal percent(String percent) {
        return Units.percentToFraction(new BigDecimal(percent.replace(',', '.')));
    }
}
