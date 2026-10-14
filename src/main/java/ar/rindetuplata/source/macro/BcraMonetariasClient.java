package ar.rindetuplata.source.macro;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceHttp;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.SourceRequestException;
import ar.rindetuplata.source.Units;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** BCRA monetary statistics v4: {@code GET /monetarias/{id}?limit=n}. Values arrive as percents. */
public class BcraMonetariasClient implements MacroSource {

    public static final SourceRef SOURCE = new SourceRef("bcra-estadisticas-monetarias", "BCRA Statistics", true);

    private final RestClient client;
    private final Clock clock;
    private final int limit;

    public BcraMonetariasClient(RestClient client, Clock clock, int limit) {
        this.client = client;
        this.clock = clock;
        this.limit = limit;
    }

    @Override
    public boolean supports(MacroVariable variable) {
        return true;
    }

    @Override
    public List<MacroObservation> fetch(MacroVariable variable) {
        Instant fetchedAt = clock.instant();
        try {
            return parse(SourceHttp.get(client, SOURCE, "/monetarias/" + variable.bcraId() + "?limit=" + limit),
                    variable, fetchedAt);
        } catch (SourceRequestException e) {
            throw bcraError(e);
        }
    }

    /** Pure: raw body → observations, newest first. Also used to replay stored responses. */
    public static List<MacroObservation> parse(byte[] body, MacroVariable variable, Instant fetchedAt) {
        JsonNode results = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), "results");
        List<MacroObservation> observations = new ArrayList<>();
        for (JsonNode result : results) {
            Integer id = JsonSupport.integer(SOURCE, result, "idVariable");
            if (id == null || id != variable.bcraId()) {
                throw new SourceException(SOURCE, "expected variable " + variable.bcraId() + " but got " + id, null);
            }
            for (JsonNode point : JsonSupport.array(SOURCE, result, "detalle")) {
                BigDecimal value = Units.percentToFraction(JsonSupport.decimal(SOURCE, point, "valor"));
                LocalDate date = JsonSupport.date(SOURCE, point, "fecha");
                List<ValidationFlag> flags = new ArrayList<>();
                if (value == null) {
                    flags.add(new ValidationFlag(FlagCode.MISSING_VALUE, Severity.INVALID, "Observation has no value"));
                }
                if (date == null) {
                    flags.add(new ValidationFlag(FlagCode.MISSING_VALUE, Severity.INVALID, "Observation has no date"));
                }
                observations.add(new MacroObservation(SOURCE, variable, date, value, fetchedAt,
                        Confidence.OFFICIAL_AUTOMATIC, flags));
            }
        }
        observations.sort(MacroObservations.NEWEST_FIRST);
        return observations;
    }

    /** Turns the documented {@code {"errorMessages": [...]}} body into a typed error; other bodies stay generic. */
    private static SourceRequestException bcraError(SourceRequestException e) {
        try {
            JsonNode messages = JsonSupport.readTree(SOURCE, e.body().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .path("errorMessages");
            if (messages.isArray() && !messages.isEmpty()) {
                List<String> texts = new ArrayList<>();
                messages.forEach(m -> texts.add(m.asString()));
                return new BcraApiException(SOURCE, e.status(), e.body(), texts);
            }
        } catch (SourceException | JacksonException ignored) {
            // not the documented error shape
        }
        return e;
    }
}
