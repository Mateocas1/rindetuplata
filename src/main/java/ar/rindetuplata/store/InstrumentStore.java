package ar.rindetuplata.store;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Upserts the instrument a source record belongs to. One row per (kind, source, natural key); the upsert refreshes
 * the display fields and widens {@code first_seen}/{@code last_seen}, never narrows them (T4 design notes).
 */
@Component
public class InstrumentStore {

	private static final String UPSERT = """
			insert into instrument (kind, source_id, natural_key, display_name, entity_name, segment, first_seen, last_seen)
			values (:kind, :sourceId, :naturalKey, :displayName, :entityName, :segment, :seen, :seen)
			on conflict (kind, source_id, natural_key) do update
			set display_name = excluded.display_name,
			    entity_name = excluded.entity_name,
			    segment = excluded.segment,
			    first_seen = least(instrument.first_seen, excluded.first_seen),
			    last_seen = greatest(instrument.last_seen, excluded.last_seen)
			returning id
			""";

	private final JdbcClient jdbc;

	public InstrumentStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public long upsert(InstrumentKind kind, String sourceId, String naturalKey, String displayName, String entityName,
			String segment, LocalDate seenDate) {
		return jdbc.sql(UPSERT)
				.param("kind", kind.name())
				.param("sourceId", sourceId)
				.param("naturalKey", naturalKey)
				.param("displayName", displayName == null ? naturalKey : displayName)
				.param("entityName", entityName)
				.param("segment", segment)
				.param("seen", seenDate)
				.query(Long.class)
				.single();
	}
}
