package ar.rindetuplata.source.plazofijo;

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

/** ArgentinaDatos: {@code GET /tasas/plazoFijo}. TNA arrives as a fraction ({@code 0.1925}); no history. */
public class ArgentinaDatosPlazoFijoAdapter implements BankPlazoFijoSource {

    public static final SourceRef SOURCE = new SourceRef("argentinadatos-plazo-fijo", "ArgentinaDatos", false);

    private final RestClient client;
    private final Clock clock;

    public ArgentinaDatosPlazoFijoAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<BankPlazoFijoRate> fetch() {
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, "/tasas/plazoFijo"), fetchedAt);
    }

    /** Pure: raw body → records. Also used to replay the T9 archive. */
    public static List<BankPlazoFijoRate> parse(byte[] body, Instant fetchedAt) {
        JsonNode rows = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), null);
        LocalDate snapshotDate = ValidationRules.localDate(fetchedAt);
        List<BankPlazoFijoRate> rates = new ArrayList<>();
        for (JsonNode row : rows) {
            BigDecimal clientTna = Units.normalizeFraction(JsonSupport.decimal(SOURCE, row, "tnaClientes"));
            BigDecimal nonClientTna = Units.normalizeFraction(JsonSupport.decimal(SOURCE, row, "tnaNoClientes"));
            if (nonClientTna != null && nonClientTna.signum() == 0) {
                nonClientTna = null;
            }
            // A missing rate is absent (not offered), not invalid; a row needs at least one of the two (S19).
            List<ValidationFlag> flags = new ArrayList<>();
            if (clientTna != null || nonClientTna == null) {
                flags.addAll(ValidationRules.rateFlags(clientTna));
            }
            if (nonClientTna != null) {
                flags.addAll(ValidationRules.rateFlags(nonClientTna));
            }
            List<TermRate> terms = new ArrayList<>();
            JsonNode bands = row.path("tasas");
            if (bands.isArray()) {
                for (JsonNode band : bands) {
                    BigDecimal tna = Units.normalizeFraction(JsonSupport.decimal(SOURCE, band, "tna"));
                    flags.addAll(ValidationRules.rateFlags(tna));
                    terms.add(new TermRate(
                            JsonSupport.integer(SOURCE, band, "plazoMinDias"),
                            JsonSupport.integer(SOURCE, band, "plazoMaxDias"),
                            JsonSupport.decimal(SOURCE, band, "montoMinimo"),
                            JsonSupport.decimal(SOURCE, band, "montoMaximo"),
                            tna));
                }
            }
            rates.add(new BankPlazoFijoRate(SOURCE, JsonSupport.text(row, "entidad"), clientTna, nonClientTna, terms,
                    snapshotDate, fetchedAt, Confidence.AGGREGATOR_AUTOMATIC, flags));
        }
        return rates;
    }
}
