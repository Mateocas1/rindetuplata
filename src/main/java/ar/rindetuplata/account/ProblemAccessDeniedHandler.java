package ar.rindetuplata.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * A forbidden request (a missing role or a bad CSRF token) is a 403 JSON {@code ProblemDetail}. Spring Security
 * gives the CSRF filter this same handler, so a failed CSRF check and a missing role answer alike.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ProblemResponses.write(request, response, HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
	}
}
