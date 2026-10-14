package ar.rindetuplata.source.plazofijo;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.FlagCode;
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

/** BCRA transparency regime: {@code GET /PlazosFijos}. TEA arrives as a percent ({@code 21.1}). */
public class BcraPlazoFijoAdapter implements OfficialPlazoFijoSource {

    public static final SourceRef SOURCE = new SourceRef("bcra-transparencia-plazos-fijos", "BCRA Transparency", true);

    private final RestClient client;
    private final Clock clock;

    public BcraPlazoFijoAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<OfficialPlazoFijoRate> fetch() {
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, "/PlazosFijos"), fetchedAt);
    }

    /** Pure: raw body → records. Also used to replay the T9 archive (pass the capture instant as {@code fetchedAt}). */
    public static List<OfficialPlazoFijoRate> parse(byte[] body, Instant fetchedAt) {
        JsonNode rows = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), "results");
        LocalDate today = ValidationRules.localDate(fetchedAt);
        List<OfficialPlazoFijoRate> rates = new ArrayList<>();
        for (JsonNode row : rows) {
            BigDecimal tea = Units.percentToFraction(JsonSupport.decimal(SOURCE, row, "tasaEfectivaAnualMinima"));
            LocalDate dataDate = JsonSupport.date(SOURCE, row, "fechaInformacion");
            String productName = firstNonNull(JsonSupport.text(row, "nombreCorto"), JsonSupport.text(row, "nombreCompleto"));
            List<ValidationFlag> flags = new ArrayList<>(ValidationRules.rateFlags(tea));
            flags.addAll(ValidationRules.stalenessFlags(dataDate, today, ValidationRules.PLAZO_FIJO_STALE_DAYS));
            flags.add(new ValidationFlag(FlagCode.TERM_CODE_UNDOCUMENTED, Severity.WARNING,
                    "plazoMinimoInvertirDias is an undocumented 0-5 code, not a number of days"));
            rates.add(new OfficialPlazoFijoRate(
                    SOURCE,
                    JsonSupport.integer(SOURCE, row, "codigoEntidad"),
                    JsonSupport.text(row, "descripcionEntidad"),
                    productName,
                    ValidationRules.segmentOf(productName),
                    JsonSupport.integer(SOURCE, row, "plazoMinimoInvertirDias"),
                    JsonSupport.decimal(SOURCE, row, "montoMinimoInvertir"),
                    JsonSupport.text(row, "canalConstitucion"),
                    tea,
                    dataDate,
                    fetchedAt,
                    Confidence.OFFICIAL_AUTOMATIC,
                    flags));
        }
        return rates;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}
