package ar.rindetuplata.apikey;

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
 * The {@code api_key} table of V10. Every query that serves a user is scoped by the owner's email, so no endpoint can
 * read or revoke a key of somebody else; the only unscoped read is {@link #findActiveByHash}, the lookup of the
 * rate limit. The plaintext key never reaches this class: only its hash and display prefix do.
 */
@Component
class ApiKeyRepository {

	private static final String COLUMNS = "id, name, key_prefix, plan, created_at";

	private final JdbcClient jdbc;

	ApiKeyRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Locks the user's {@code app_user} row until the transaction ends, so creates of one user queue up. */
	void lockUser(String email) {
		List<Long> locked = jdbc.sql("select id from app_user where email = :email for update")
				.param("email", email).query(Long.class).list();
		if (locked.isEmpty()) {
			throw new IllegalStateException("No account for the authenticated email " + email);
		}
	}

	long countActive(String email) {
		return jdbc.sql("""
				select count(*) from api_key
				where revoked_at is null and user_id = (select id from app_user where email = :email)
				""").param("email", email).query(Long.class).single();
	}

	StoredKey insert(String email, String name, String prefix, String hash, Instant now) {
		return jdbc.sql("""
				insert into api_key (user_id, name, key_prefix, key_hash, plan, created_at)
				select id, :name, :prefix, :hash, 'FREE', :now from app_user where email = :email
				returning""" + " " + COLUMNS)
				.param("name", name)
				.param("prefix", prefix)
				.param("hash", hash)
				.param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
				.param("email", email)
				.query(ApiKeyRepository::map)
				.single();
	}

	/** The active keys of one user, oldest first, so the page is stable. */
	List<StoredKey> findActive(String email) {
		return jdbc.sql("select " + COLUMNS + """
				 from api_key
				where revoked_at is null and user_id = (select id from app_user where email = :email)
				order by id
				""").param("email", email).query(ApiKeyRepository::map).list();
	}

	/** Revokes one of the user's own active keys; zero means it is unknown, somebody else's or already revoked. */
	int revoke(String email, long id, Instant now) {
		return jdbc.sql("""
				update api_key set revoked_at = :now
				where id = :id and revoked_at is null and user_id = (select id from app_user where email = :email)
				""")
				.param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
				.param("id", id)
				.param("email", email)
				.update();
	}

	Optional<ApiKeyPrincipal> findActiveByHash(String hash) {
		return jdbc.sql("select id, plan from api_key where key_hash = :hash and revoked_at is null")
				.param("hash", hash)
				.query((rs, rowNumber) -> new ApiKeyPrincipal(rs.getLong("id"), ApiKeyPlan.valueOf(rs.getString("plan"))))
				.optional();
	}

	private static StoredKey map(ResultSet rs, int rowNumber) throws SQLException {
		return new StoredKey(rs.getLong("id"), rs.getString("name"), rs.getString("key_prefix"),
				ApiKeyPlan.valueOf(rs.getString("plan")), rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	/** One stored key without its hash. */
	record StoredKey(long id, String name, String prefix, ApiKeyPlan plan, Instant createdAt) {
	}
}
