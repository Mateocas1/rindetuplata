package ar.rindetuplata.account;

import ar.rindetuplata.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.InvalidOneTimeTokenException;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Service;

/**
 * The magic-link flow of T6a (S13). A request never says whether the email is known: an invalid email is a 400,
 * anything else is accepted, and the mail is skipped silently once the email holds
 * {@code rindetuplata.auth.max-active-tokens} unexpired tokens. A login consumes the token once, creates the
 * account on the first success and returns the email with its authorities.
 */
@Service
public class AccountService {

	private final OneTimeTokenService tokens;

	private final AccountRepository accounts;

	private final AccountRoles roles;

	private final MagicLinkMailComposer composer;

	private final Mailer mailer;

	private final AccountProperties properties;

	private final Clock clock;

	public AccountService(OneTimeTokenService tokens, AccountRepository accounts, AccountRoles roles,
			MagicLinkMailComposer composer, Mailer mailer, AccountProperties properties, Clock clock) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.roles = roles;
		this.composer = composer;
		this.mailer = mailer;
		this.properties = properties;
		this.clock = clock;
	}

	/** Always the same outcome for a valid email: accepted, mail sent unless the token cap blocks it. */
	public void requestMagicLink(String rawEmail) {
		String email = Emails.normalize(rawEmail);
		if (!Emails.isValid(email)) {
			throw ApiException.badRequest("email", "Email address '" + rawEmail + "' is not valid");
		}
		if (accounts.countUnexpiredTokens(email, clock.instant()) >= properties.maxActiveTokens()) {
			return;
		}
		OneTimeToken token = tokens.generate(new GenerateOneTimeTokenRequest(email, properties.tokenTtl()));
		mailer.send(composer.compose(email, token.getTokenValue()));
	}

	/** Consumes the one-time token and returns the account, creating it if this is the first login. */
	public AuthenticatedAccount login(String tokenValue) {
		if (tokenValue == null || tokenValue.isBlank()) {
			throw AccountException.unauthorized("A login token is required");
		}
		OneTimeToken token;
		try {
			token = tokens.consume(new OneTimeTokenAuthenticationToken(tokenValue));
		} catch (InvalidOneTimeTokenException exception) {
			token = null;
		}
		// The JDBC service answers null for an unknown token and deletes a token as it consumes it, so a second
		// use also lands here; other implementations throw instead, and both are the same 401.
		if (token == null) {
			throw AccountException.unauthorized("The login token is unknown, already used or expired");
		}
		String email = Emails.normalize(token.getUsername());
		Instant now = clock.instant();
		AppUser user = accounts.findByEmail(email).orElseGet(() -> accounts.create(email, now));
		accounts.recordLogin(user.id(), now);
		return new AuthenticatedAccount(email, roles.of(email));
	}
}
