package ar.rindetuplata.store;

import ar.rindetuplata.source.macro.MacroObservation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one value per (variable, observation date, source). A later run corrects a value instead of duplicating it;
 * two sources of the same variable keep their own rows, so they can be compared. An observation without a date in
 * its own series would break the key: it is stored under the capture date rather than dropped.
 */
@Component
public class MacroObservationWriter {

	private static final String INSERT = """
			insert into macro_observation (variable, observation_date, source_id, value, fetched_at, confidence, flags)
			values (:variable, :observationDate, :sourceId, :value, :fetchedAt, :confidence, cast(:flags as jsonb))
			on conflict (variable, observation_date, source_id) do update
			set value = excluded.value,
			    fetched_at = excluded.fetched_at,
			    confidence = excluded.confidence,
			    flags = excluded.flags
			""";

	private final JdbcClient jdbc;

	public MacroObservationWriter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public WriteResult write(List<MacroObservation> observations, LocalDate captureDate) {
		Map<String, MacroObservation> distinct = new LinkedHashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (MacroObservation observation : observations) {
			String key = key(observation, captureDate);
			if (distinct.putIfAbsent(key, observation) != null) {
				duplicates.add(key);
			}
		}
		for (Map.Entry<String, MacroObservation> entry : distinct.entrySet()) {
			MacroObservation observation = entry.getValue();
			jdbc.sql(INSERT)
					.param("variable", observation.variable().name())
					.param("observationDate", observation.date() == null ? captureDate : observation.date())
					.param("sourceId", observation.source().id())
					.param("value", observation.value())
					.param("fetchedAt", InstantTimes.of(observation.fetchedAt()))
					.param("confidence", observation.confidence().name())
					.param("flags", StoreJson.flags(observation.flags()))
					.update();
		}
		return WriteResult.of(distinct.size(), duplicates);
	}

	private static String key(MacroObservation observation, LocalDate captureDate) {
		LocalDate date = observation.date() == null ? captureDate : observation.date();
		return observation.variable().name() + "|" + date + "|" + observation.source().id();
	}
}
