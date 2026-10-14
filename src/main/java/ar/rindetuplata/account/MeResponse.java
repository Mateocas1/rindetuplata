package ar.rindetuplata.account;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/** The current user: {@code GET /api/v1/me} and the 200 body of a successful {@code POST /api/v1/auth/login}. */
public record MeResponse(String email, List<String> roles) {

	public static MeResponse of(AuthenticatedAccount account) {
		return new MeResponse(account.email(), account.authorities().stream().map(GrantedAuthority::getAuthority).toList());
	}
}
