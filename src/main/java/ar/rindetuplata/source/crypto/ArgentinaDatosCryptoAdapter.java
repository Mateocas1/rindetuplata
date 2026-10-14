package ar.rindetuplata.source.crypto;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.SourceHttp;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.Units;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** ArgentinaDatos: {@code GET /rendimientos}. APY arrives as a percent ({@code 6.2}). */
public class ArgentinaDatosCryptoAdapter implements CryptoYieldSource {

    public static final SourceRef SOURCE = new SourceRef("argentinadatos-rendimientos", "ArgentinaDatos", false);

    private final RestClient client;
    private final Clock clock;

    public ArgentinaDatosCryptoAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<CryptoYield> fetch() {
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, "/rendimientos"), fetchedAt);
    }

    /** Pure: raw body → records. Also used to replay the T9 archive. */
    public static List<CryptoYield> parse(byte[] body, Instant fetchedAt) {
        JsonNode platforms = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), null);
        LocalDate today = ValidationRules.localDate(fetchedAt);
        List<CryptoYield> yields = new ArrayList<>();
        for (JsonNode platform : platforms) {
            for (JsonNode row : JsonSupport.array(SOURCE, platform, "rendimientos")) {
                BigDecimal apy = Units.percentToFraction(JsonSupport.decimal(SOURCE, row, "apy"));
                LocalDate date = JsonSupport.date(SOURCE, row, "fecha");
                List<ValidationFlag> flags = new ArrayList<>(ValidationRules.rateFlags(apy));
                flags.addAll(ValidationRules.stalenessFlags(date, today, ValidationRules.CRYPTO_STALE_DAYS));
                yields.add(new CryptoYield(SOURCE, JsonSupport.text(platform, "entidad"),
                        JsonSupport.text(row, "moneda"), apy, date, fetchedAt, Confidence.AGGREGATOR_AUTOMATIC, flags));
            }
        }
        return yields;
    }
}
