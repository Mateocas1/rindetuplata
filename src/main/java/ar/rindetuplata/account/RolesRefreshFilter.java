package ar.rindetuplata.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recomputes the authorities of an authenticated session on every request (T11a, S4). The session stores the roles
 * it was created with; this replaces them with {@link AccountRoles#of} for the current
 * {@code rindetuplata.auth.admin-emails}, so an email removed from that list loses {@code ROLE_ADMIN} on its next
 * request without logging in again. The refreshed token lives only for the request: it is never saved back to the
 * session ({@code requireExplicitSave}), and the session keeps working otherwise.
 */
final class RolesRefreshFilter extends OncePerRequestFilter {

	private final AccountRoles roles;

	RolesRefreshFilter(AccountRoles roles) {
		this.roles = roles;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication current = context.getAuthentication();
		if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(current.getPrincipal(),
					current.getCredentials(), roles.of(current.getName())));
		}
		chain.doFilter(request, response);
	}
}
