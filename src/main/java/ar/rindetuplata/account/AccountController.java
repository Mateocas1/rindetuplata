package ar.rindetuplata.account;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The account API of T6a (S13): request a magic link, log in with its token, and read the current user. The login
 * authenticates by consuming the one-time token and stores the context in the Spring Session, so the session
 * cookie it returns identifies the following requests. Like Spring Security's login filters, it changes the session
 * id and the CSRF token on success.
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

	/**
	 * The T7a contract the SPA is written against: the login rotates the CSRF token, so the client refetches
	 * {@code GET /api/v1/auth/csrf} instead of reusing the token it used for the login itself.
	 */
	private static final String LOGIN_DESCRIPTION = "Consumes the one-time token of the magic link and starts the "
			+ "session. A successful login rotates the CSRF token and clears the XSRF-TOKEN cookie, so the client "
			+ "must call GET /api/v1/auth/csrf again before its next state-changing request.";

	private static final String MAGIC_LINK_DESCRIPTION = "Emails a one-time login link. A valid email always answers "
			+ "202 with the same body, whether or not the account exists and even when the email already holds its "
			+ "maximum of unexpired tokens (then nothing is sent). An invalid email is a 400. Each remote address may "
			+ "make rindetuplata.auth.magic-link-per-ip-limit requests per rindetuplata.auth.magic-link-per-ip-window "
			+ "(fixed window, counted in memory by the single API instance, invalid emails included); beyond it the "
			+ "answer is a 429 with a Retry-After header in seconds, no token and no mail, for known and unknown "
			+ "emails alike.";

	private final AccountService accounts;

	private final MagicLinkRateLimiter magicLinkLimiter;

	private final SecurityContextRepository securityContextRepository;

	private final SessionAuthenticationStrategy sessionStrategy;

	public AccountController(AccountService accounts, MagicLinkRateLimiter magicLinkLimiter,
			SecurityContextRepository securityContextRepository, SessionAuthenticationStrategy sessionStrategy) {
		this.accounts = accounts;
		this.magicLinkLimiter = magicLinkLimiter;
		this.securityContextRepository = securityContextRepository;
		this.sessionStrategy = sessionStrategy;
	}

	@GetMapping("/auth/csrf")
	@Operation(summary = "Current CSRF token", description = "The raw CSRF token the SPA sends as the "
			+ "X-XSRF-TOKEN header on every state-changing request; the same response writes the XSRF-TOKEN cookie. "
			+ "The header is compared against the raw value, so this is the token to hand the client, never the "
			+ "masked request attribute. Public: no session is needed to ask for one.")
	CsrfTokenResponse csrf(HttpServletRequest request) {
		DeferredCsrfToken deferred = (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
		if (deferred == null) {
			throw new IllegalStateException("the CSRF filter did not expose its deferred token");
		}
		CsrfToken token = deferred.get();
		return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
	}

	@PostMapping("/auth/magic-link")
	@Operation(summary = "Request a magic link", description = MAGIC_LINK_DESCRIPTION)
	ResponseEntity<MagicLinkResponse> requestMagicLink(@RequestBody MagicLinkRequest request,
			HttpServletRequest httpRequest) {
		// First, before the email is even validated: an invalid email still counts, and the answer never depends on
		// whether the email is known or already holds its maximum of tokens.
		OptionalLong retryAfter = magicLinkLimiter.tryAcquire(httpRequest.getRemoteAddr());
		if (retryAfter.isPresent()) {
			throw AccountException.tooManyRequests("Too many magic-link requests from this address; try again later",
					retryAfter.getAsLong());
		}
		accounts.requestMagicLink(request == null ? null : request.email());
		return ResponseEntity.accepted().body(MagicLinkResponse.ACCEPTED);
	}

	@PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Log in with a magic-link token", description = LOGIN_DESCRIPTION)
	ResponseEntity<MeResponse> loginJson(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return login(request == null ? null : request.token(), httpRequest, httpResponse);
	}

	@PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@Operation(summary = "Log in with a magic-link token", description = LOGIN_DESCRIPTION)
	ResponseEntity<MeResponse> loginForm(@RequestParam("token") String token, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return login(token, httpRequest, httpResponse);
	}

	@GetMapping("/me")
	MeResponse me(Authentication authentication) {
		List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		return new MeResponse(authentication.getName(), roles);
	}

	private ResponseEntity<MeResponse> login(String token, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AuthenticatedAccount account = accounts.login(token);
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(account.email(), null,
				account.authorities());
		sessionStrategy.onAuthentication(authentication, httpRequest, httpResponse);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
		return ResponseEntity.ok(MeResponse.of(account));
	}
}
