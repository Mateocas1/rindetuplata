package ar.rindetuplata.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Anonymous access to a protected endpoint is a 401 JSON {@code ProblemDetail}, never a redirect to a login page:
 * the API has no HTML login, the frontend asks for a magic link.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authenticationException) throws IOException {
		ProblemResponses.write(request, response, HttpStatus.UNAUTHORIZED,
				"Authentication is required to access this resource");
	}
}
