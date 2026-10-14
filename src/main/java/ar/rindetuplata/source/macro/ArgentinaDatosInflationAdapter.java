package ar.rindetuplata.source.macro;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceHttp;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.Units;
import ar.rindetuplata.source.ValidationFlag;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** ArgentinaDatos: {@code GET /indices/inflacion}, secondary source for {@link MacroVariable#MONTHLY_INFLATION}. */
public class ArgentinaDatosInflationAdapter implements MacroSource {

    public static final SourceRef SOURCE = new SourceRef("argentinadatos-inflacion", "ArgentinaDatos", false);

    private final RestClient client;
    private final Clock clock;

    public ArgentinaDatosInflationAdapter(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public boolean supports(MacroVariable variable) {
        return variable == MacroVariable.MONTHLY_INFLATION;
    }

    @Override
    public List<MacroObservation> fetch(MacroVariable variable) {
        if (!supports(variable)) {
            throw new IllegalArgumentException(SOURCE.id() + " only provides " + MacroVariable.MONTHLY_INFLATION);
        }
        Instant fetchedAt = clock.instant();
        return parse(SourceHttp.get(client, SOURCE, "/indices/inflacion"), fetchedAt);
    }

    /** Pure: raw body → observations, newest first. */
    public static List<MacroObservation> parse(byte[] body, Instant fetchedAt) {
        JsonNode rows = JsonSupport.array(SOURCE, JsonSupport.readTree(SOURCE, body), null);
        List<MacroObservation> observations = new ArrayList<>();
        for (JsonNode row : rows) {
            BigDecimal value = Units.percentToFraction(JsonSupport.decimal(SOURCE, row, "valor"));
            LocalDate date = JsonSupport.date(SOURCE, row, "fecha");
            List<ValidationFlag> flags = new ArrayList<>();
            if (value == null) {
                flags.add(new ValidationFlag(FlagCode.MISSING_VALUE, Severity.INVALID, "Observation has no value"));
            }
            if (date == null) {
                flags.add(new ValidationFlag(FlagCode.MISSING_VALUE, Severity.INVALID, "Observation has no date"));
            }
            observations.add(new MacroObservation(SOURCE, MacroVariable.MONTHLY_INFLATION, date, value, fetchedAt,
                    Confidence.AGGREGATOR_AUTOMATIC, flags));
        }
        observations.sort(MacroObservations.NEWEST_FIRST);
        return observations;
    }
}
