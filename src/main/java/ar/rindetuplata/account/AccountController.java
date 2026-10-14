package ar.rindetuplata.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
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

	private final AccountService accounts;

	private final SecurityContextRepository securityContextRepository;

	private final SessionAuthenticationStrategy sessionStrategy;

	public AccountController(AccountService accounts, SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionStrategy) {
		this.accounts = accounts;
		this.securityContextRepository = securityContextRepository;
		this.sessionStrategy = sessionStrategy;
	}

	@PostMapping("/auth/magic-link")
	ResponseEntity<MagicLinkResponse> requestMagicLink(@RequestBody MagicLinkRequest request) {
		accounts.requestMagicLink(request == null ? null : request.email());
		return ResponseEntity.accepted().body(MagicLinkResponse.ACCEPTED);
	}

	@PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<MeResponse> loginJson(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		return login(request == null ? null : request.token(), httpRequest, httpResponse);
	}

	@PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
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
