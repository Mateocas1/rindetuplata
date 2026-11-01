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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * ArgentinaDatos: {@code GET /cotizaciones/dolares/bolsa}, the whole MEP series with one row per calendar day
 * (2,926 rows from 2018-10-29 when probed on 2026-11-01). The backfill (T4a) reads it once per run and stores it
 * under its own source id, so it never collides with the {@code dolarapi-bolsa} rows of the daily job.
 */
public class ArgentinaDatosDollarAdapter implements DollarHistorySource {

    public static final SourceRef SOURCE = new SourceRef("argentinadatos-dolar-bolsa", "ArgentinaDatos", false);

    private final RestClient client;
    private final Clock clock;

    public ArgentinaDatosDollarAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<DatedDollarQuote> fetchSeries() {
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, "/dolares/bolsa"), fetchedAt);
    }

    /** Pure: raw body → one dated quote per row, in the order of the source. */
    public static List<DatedDollarQuote> parse(byte[] body, Instant fetchedAt) {
        JsonNode rows = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), null);
        List<DatedDollarQuote> series = new ArrayList<>();
        for (JsonNode row : rows) {
            String house = JsonSupport.text(row, "casa");
            if (!"bolsa".equals(house)) {
                throw new SourceException(SOURCE, "expected casa 'bolsa' but got " + house, null);
            }
            LocalDate date = JsonSupport.date(SOURCE, row, "fecha");
            if (date == null) {
                throw new SourceException(SOURCE, "a row has no 'fecha'", null);
            }
            BigDecimal buy = JsonSupport.decimal(SOURCE, row, "compra");
            BigDecimal sell = JsonSupport.decimal(SOURCE, row, "venta");
            List<ValidationFlag> flags = new ArrayList<>();
            addIfNotPositive(flags, "compra", buy);
            addIfNotPositive(flags, "venta", sell);
            series.add(new DatedDollarQuote(date,
                    new DollarQuote(SOURCE, buy, sell, null, fetchedAt, Confidence.AGGREGATOR_AUTOMATIC, flags)));
        }
        return series;
    }

    private static void addIfNotPositive(List<ValidationFlag> flags, String field, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            flags.add(new ValidationFlag(FlagCode.INVALID_QUOTE, Severity.INVALID, field + " is missing or not positive"));
        }
    }
}
