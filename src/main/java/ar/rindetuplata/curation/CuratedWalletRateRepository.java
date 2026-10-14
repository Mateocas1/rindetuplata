package ar.rindetuplata.curation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The V4 tables of T6b (S12) through {@link JdbcClient}: the curated wallet rates the adapter and the ranking read,
 * and their audit trail. Writes are upserts, so a save replaces a row and re-running an action changes nothing else.
 */
@Component
public class CuratedWalletRateRepository {

	private static final String SELECT_ALL = """
			select wallet_id, name, tiers, conditions, note, verified_at, updated_at, updated_by
			from curated_wallet_rate order by wallet_id
			""";

	private static final String SELECT_ONE = """
			select wallet_id, name, tiers, conditions, note, verified_at, updated_at, updated_by
			from curated_wallet_rate where wallet_id = :walletId
			""";

	private static final String UPSERT = """
			insert into curated_wallet_rate (wallet_id, name, tiers, conditions, note, verified_at, updated_at, updated_by)
			values (:walletId, :name, cast(:tiers as jsonb), cast(:conditions as jsonb), :note, :verifiedAt, :updatedAt,
			        :updatedBy)
			on conflict (wallet_id) do update
			set name = excluded.name,
			    tiers = excluded.tiers,
			    conditions = excluded.conditions,
			    note = excluded.note,
			    verified_at = excluded.verified_at,
			    updated_at = excluded.updated_at,
			    updated_by = excluded.updated_by
			""";

	private static final String INSERT_AUDIT = """
			insert into curated_wallet_rate_audit (wallet_id, action, payload, actor, at)
			values (:walletId, :action, cast(:payload as jsonb), :actor, :at)
			""";

	private static final String SELECT_AUDIT = """
			select id, wallet_id, action, payload, actor, at from curated_wallet_rate_audit
			where wallet_id = :walletId order by at desc, id desc
			""";

	private final JdbcClient jdbc;

	public CuratedWalletRateRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<CuratedWalletRate> findAll() {
		return jdbc.sql(SELECT_ALL).query(CuratedWalletRateRepository::map).list();
	}

	public Optional<CuratedWalletRate> find(String walletId) {
		return jdbc.sql(SELECT_ONE).param("walletId", walletId).query(CuratedWalletRateRepository::map).optional();
	}

	public void upsert(CuratedWalletRate rate) {
		jdbc.sql(UPSERT)
				.param("walletId", rate.walletId())
				.param("name", rate.name())
				.param("tiers", CurationJson.write(rate.tiers()))
				.param("conditions", CurationJson.write(rate.conditions()))
				.param("note", rate.note())
				.param("verifiedAt", rate.verifiedAt())
				.param("updatedAt", rate.updatedAt())
				.param("updatedBy", rate.updatedBy())
				.update();
	}

	public void record(String walletId, String action, String payload, String actor, OffsetDateTime at) {
		jdbc.sql(INSERT_AUDIT)
				.param("walletId", walletId)
				.param("action", action)
				.param("payload", payload)
				.param("actor", actor)
				.param("at", at)
				.update();
	}

	public List<AuditEntryView> audit(String walletId) {
		return jdbc.sql(SELECT_AUDIT).param("walletId", walletId).query(CuratedWalletRateRepository::mapAudit).list();
	}

	/** The document as stored: what an audit entry keeps, so a past action stays readable. */
	static String payload(CuratedWalletRate rate) {
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("walletId", rate.walletId());
		document.put("name", rate.name());
		document.put("tiers", rate.tiers());
		document.put("conditions", rate.conditions());
		document.put("note", rate.note());
		document.put("verifiedAt", rate.verifiedAt() == null ? null : rate.verifiedAt().toString());
		return CurationJson.write(document);
	}

	private static CuratedWalletRate map(ResultSet rs, int rowNumber) throws SQLException {
		return new CuratedWalletRate(rs.getString("wallet_id"), rs.getString("name"),
				CurationJson.read(rs.getString("tiers")), CurationJson.read(rs.getString("conditions")),
				rs.getString("note"), rs.getObject("verified_at", LocalDate.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("updated_by"));
	}

	private static AuditEntryView mapAudit(ResultSet rs, int rowNumber) throws SQLException {
		return new AuditEntryView(rs.getLong("id"), rs.getString("wallet_id"), rs.getString("action"),
				CurationJson.read(rs.getString("payload")), rs.getString("actor"),
				rs.getObject("at", OffsetDateTime.class));
	}
}
