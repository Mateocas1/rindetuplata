package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedKind;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The alert tables of V5. The rule queries are scoped by the owner's email, so no endpoint can read or delete a
 * rule that belongs to somebody else; {@link #findActive()} is the evaluation's read and joins the owner's email so
 * a fired event can name it without another query.
 */
@Component
public class AlertRuleRepository {

	private static final String COLUMNS = "id, user_id, kind, instrument_id, threshold_rate, active, created_at";

	private final JdbcClient jdbc;

	public AlertRuleRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The rules of one user, oldest first, so the UI is stable. */
	public List<AlertRule> findByEmail(String email) {
		return jdbc.sql("select " + COLUMNS + " from alert_rule where user_id = (select id from app_user where email = :email)"
				+ " order by id")
			.param("email", email)
			.query((rs, rowNumber) -> map(rs, email))
			.list();
	}

	/** Every active rule of every user, with its owner's email: the input of one evaluation. */
	public List<AlertRule> findActive() {
		return jdbc.sql("""
				select r.id, r.user_id, r.kind, r.instrument_id, r.threshold_rate, r.active, r.created_at, u.email
				from alert_rule r join app_user u on u.id = r.user_id
				where r.active
				order by r.id
				""")
			.query((rs, rowNumber) -> map(rs, rs.getString("email")))
			.list();
	}

	public long countActive(String email) {
		return jdbc.sql("""
				select count(*) from alert_rule
				where active and user_id = (select id from app_user where email = :email)
				""")
			.param("email", email)
			.query(Long.class)
			.single();
	}

	public boolean instrumentExists(long instrumentId) {
		return jdbc.sql("select exists(select 1 from instrument where id = :instrumentId)")
			.param("instrumentId", instrumentId)
			.query(Boolean.class)
			.single();
	}

	public AlertRule insert(String email, RankedKind kind, Long instrumentId, BigDecimal thresholdRate, Instant now) {
		long userId = userId(email)
			.orElseThrow(() -> new IllegalStateException("No account for the authenticated email " + email));
		return jdbc.sql("insert into alert_rule (user_id, kind, instrument_id, threshold_rate, active, created_at)"
				+ " values (:userId, :kind, :instrumentId, :thresholdRate, true, :now) returning " + COLUMNS)
			.param("userId", userId)
			.param("kind", kind == null ? null : kind.name())
			.param("instrumentId", instrumentId)
			.param("thresholdRate", thresholdRate)
			.param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
			.query((rs, rowNumber) -> map(rs, email))
			.single();
	}

	/** Deletes one of the user's own rules; zero means it does not exist or belongs to somebody else. */
	public int delete(String email, long id) {
		return jdbc.sql("""
				delete from alert_rule
				where id = :id and user_id = (select id from app_user where email = :email)
				""")
			.param("id", id)
			.param("email", email)
			.update();
	}

	private Optional<Long> userId(String email) {
		return jdbc.sql("select id from app_user where email = :email").param("email", email)
			.query(Long.class)
			.optional();
	}

	private static AlertRule map(ResultSet rs, String email) throws SQLException {
		String kind = rs.getString("kind");
		OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
		return new AlertRule(rs.getLong("id"), rs.getLong("user_id"), email,
				kind == null ? null : RankedKind.valueOf(kind), rs.getObject("instrument_id", Long.class),
				rs.getBigDecimal("threshold_rate"), rs.getBoolean("active"), createdAt.toInstant());
	}

}
