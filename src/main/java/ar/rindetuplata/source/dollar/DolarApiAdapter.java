package ar.rindetuplata.source.dollar;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceHttp;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** dolarapi.com: {@code GET /dolares/bolsa} (what the source calls the MEP dollar). */
public class DolarApiAdapter implements DollarSource {

    public static final SourceRef SOURCE = new SourceRef("dolarapi-bolsa", "DolarApi", false);

    private final RestClient client;
    private final Clock clock;

    public DolarApiAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public DollarQuote fetchMep() {
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, "/dolares/bolsa"), fetchedAt);
    }

    public static DollarQuote parse(byte[] body, Instant fetchedAt) {
        JsonNode root = JsonSupport.readTree(SOURCE, body);
        if (!root.isObject()) {
            throw new SourceException(SOURCE, "expected a JSON object", null);
        }
        if (!"bolsa".equals(JsonSupport.text(root, "casa"))) {
            throw new SourceException(SOURCE, "expected casa 'bolsa' but got " + JsonSupport.text(root, "casa"), null);
        }
        BigDecimal buy = JsonSupport.decimal(SOURCE, root, "compra");
        BigDecimal sell = JsonSupport.decimal(SOURCE, root, "venta");
        List<ValidationFlag> flags = new ArrayList<>();
        addIfNotPositive(flags, "compra", buy);
        addIfNotPositive(flags, "venta", sell);
        return new DollarQuote(SOURCE, buy, sell, instant(root, "fechaActualizacion"), fetchedAt,
                Confidence.AGGREGATOR_AUTOMATIC, flags);
    }

    private static void addIfNotPositive(List<ValidationFlag> flags, String field, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            flags.add(new ValidationFlag(FlagCode.INVALID_QUOTE, Severity.INVALID, field + " is missing or not positive"));
        }
    }

    private static Instant instant(JsonNode root, String field) {
        String text = JsonSupport.text(root, field);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new SourceException(SOURCE, "field '" + field + "' is not an instant: " + text, e);
        }
    }
}
