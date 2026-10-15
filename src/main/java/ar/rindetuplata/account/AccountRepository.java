package ar.rindetuplata.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The accounts table and the one-time-token count of T6a, through {@link JdbcClient}. The count lives here because
 * Spring Security's {@link org.springframework.security.authentication.ott.JdbcOneTimeTokenService} only generates
 * and consumes tokens; the 3-unexpired-token cap (S13) needs this read of its table.
 */
@Component
public class AccountRepository {

	private static final String FIND = """
			select id, email, created_at, last_login_at from app_user where email = :email
			""";

	/** A concurrent first login of the same email inserts nothing here and finds the winner's row with {@link #FIND}. */
	private static final String INSERT = """
			insert into app_user (email, created_at) values (:email, :now)
			on conflict (email) do nothing
			""";

	private static final String LOCK_TOKENS = """
			select pg_advisory_xact_lock(hashtextextended(:email, 0))
			""";

	private static final String RECORD_LOGIN = """
			update app_user set last_login_at = :now where id = :id
			""";

	/**
	 * Counts the tokens that can still be used. {@code expires_at} is a {@code timestamp} column and Spring
	 * Security writes it with {@link Timestamp#from(Instant)}, so the bound value uses the same conversion.
	 */
	private static final String COUNT_UNEXPIRED_TOKENS = """
			select count(*) from one_time_tokens where username = :email and expires_at > :now
			""";

	private final JdbcClient jdbc;

	public AccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<AppUser> findByEmail(String email) {
		return jdbc.sql(FIND).param("email", email).query(AccountRepository::map).optional();
	}

	/**
	 * The account of an email, created by the first login. The insert ignores a conflict and the row is read
	 * afterwards, so two first logins of one email (two tokens) both end with the same single row.
	 */
	public AppUser findOrCreate(String email, Instant now) {
		return create(email, now);
	}

	/** Inserts the account unless it exists and returns the stored row either way. */
	public AppUser create(String email, Instant now) {
		jdbc.sql(INSERT).param("email", email).param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();
		return findByEmail(email).orElseThrow(() -> new IllegalStateException("No account for " + email));
	}

	/**
	 * Takes the transaction-scoped advisory lock of an email's tokens (released at commit or rollback), so counting
	 * and generating tokens for one email cannot interleave. Must run inside a transaction.
	 */
	public void lockTokensOf(String email) {
		jdbc.sql(LOCK_TOKENS).param("email", email).query().singleRow();
	}

	public void recordLogin(long id, Instant now) {
		jdbc.sql(RECORD_LOGIN).param("id", id).param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();
	}

	public long countUnexpiredTokens(String email, Instant now) {
		return jdbc.sql(COUNT_UNEXPIRED_TOKENS)
				.param("email", email)
				.param("now", Timestamp.from(now))
				.query(Long.class)
				.single();
	}

	private static AppUser map(ResultSet rs, int rowNumber) throws SQLException {
		OffsetDateTime lastLogin = rs.getObject("last_login_at", OffsetDateTime.class);
		return new AppUser(rs.getLong("id"), rs.getString("email"),
				rs.getObject("created_at", OffsetDateTime.class).toInstant(),
				lastLogin == null ? null : lastLogin.toInstant());
	}
}
