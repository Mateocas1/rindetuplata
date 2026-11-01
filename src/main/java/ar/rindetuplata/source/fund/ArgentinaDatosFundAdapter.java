package ar.rindetuplata.source.fund;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceHttp;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.SourceRequestException;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.ValidationRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * ArgentinaDatos: {@code GET /fci/mercadoDinero/{ultimo,penultimo}} and, for the backfill (T4a),
 * {@code GET /fci/mercadoDinero/yyyy/MM/dd}. Dates lag and dead classes are present.
 */
public class ArgentinaDatosFundAdapter implements FundSource, FundHistorySource {

    public static final SourceRef SOURCE = new SourceRef("argentinadatos-fci-mercado-dinero", "ArgentinaDatos", false);

    private static final String CLASS_SEPARATOR = " - ";

    private final RestClient client;
    private final Clock clock;

    public ArgentinaDatosFundAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<FundQuote> fetchLatest() {
        return fetch("/fci/mercadoDinero/ultimo");
    }

    @Override
    public List<FundQuote> fetchPrevious() {
        return fetch("/fci/mercadoDinero/penultimo");
    }

    /**
     * The classes quoted on {@code day}. A day without quotes (weekend, holiday) is a 404 {@code {"error":"Not found"}}
     * on the live API, probed 2026-11-01: that is "no data that day", an empty list, and every other error stays a
     * failure of the source. Staleness is measured against {@code day}, never against the fetch date, because the
     * backfill fetches the past.
     */
    @Override
    public List<FundQuote> fetchDay(LocalDate day) {
        Instant fetchedAt = clock.instant();
        String path = "/fci/mercadoDinero/%04d/%02d/%02d".formatted(day.getYear(), day.getMonthValue(),
                day.getDayOfMonth());
        try {
            return parse(SourceHttp.get(client, SOURCE, path), fetchedAt, day);
        } catch (SourceRequestException e) {
            if (e.status() == 404) {
                return List.of();
            }
            throw e;
        }
    }

    private List<FundQuote> fetch(String path) {
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, path), fetchedAt);
    }

    /**
     * Pure: raw body → records. A class whose date lags the newest date of the same response, or the fetch date,
     * by more than {@value ValidationRules#FUND_STALE_DAYS} days is flagged {@code STALE}.
     */
    public static List<FundQuote> parse(byte[] body, Instant fetchedAt) {
        return parse(body, fetchedAt, ValidationRules.localDate(fetchedAt));
    }

    /** As {@link #parse(byte[], Instant)}, measuring staleness against {@code fetchDate} instead of the fetch instant. */
    static List<FundQuote> parse(byte[] body, Instant fetchedAt, LocalDate fetchDate) {
        JsonNode rows = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), null);
        List<LocalDate> dates = new ArrayList<>();
        for (JsonNode row : rows) {
            dates.add(JsonSupport.date(SOURCE, row, "fecha"));
        }
        LocalDate newest = dates.stream().filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        LocalDate reference = newest == null || fetchDate.isAfter(newest) ? fetchDate : newest;

        List<FundQuote> quotes = new ArrayList<>();
        int index = 0;
        for (JsonNode row : rows) {
            LocalDate date = dates.get(index++);
            String name = JsonSupport.text(row, "fondo");
            BigDecimal vcp = JsonSupport.decimal(SOURCE, row, "vcp");
            List<ValidationFlag> flags = new ArrayList<>();
            if (vcp == null || vcp.signum() <= 0) {
                flags.add(new ValidationFlag(FlagCode.INVALID_QUOTE, Severity.INVALID, "vcp is missing or not positive"));
            }
            if (date != null) {
                flags.addAll(ValidationRules.stalenessFlags(date, reference, ValidationRules.FUND_STALE_DAYS));
            }
            int split = name == null ? -1 : name.indexOf(CLASS_SEPARATOR);
            quotes.add(new FundQuote(
                    SOURCE,
                    name,
                    split < 0 ? name : name.substring(0, split),
                    split < 0 ? null : name.substring(split + CLASS_SEPARATOR.length()),
                    JsonSupport.text(row, "horizonte"),
                    ValidationRules.segmentOf(name),
                    date,
                    vcp,
                    JsonSupport.decimal(SOURCE, row, "ccp"),
                    JsonSupport.decimal(SOURCE, row, "patrimonio"),
                    fetchedAt,
                    Confidence.AGGREGATOR_AUTOMATIC,
                    flags));
        }
        return quotes;
    }
}
