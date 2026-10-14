package ar.rindetuplata.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the same RFC 9457 {@code ProblemDetail} body that {@code ApiProblemAdvice} produces, from inside the
 * security filter chain, where no {@code HandlerExceptionResolver} runs: a 401 from the entry point and a 403 from
 * the access-denied handler (including a failed CSRF check) must still be JSON, never a redirect or an HTML page.
 */
final class ProblemResponses {

	static final String CONTENT_TYPE = "application/problem+json";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private ProblemResponses() {
	}

	static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", request.getRequestURI());

		response.setStatus(status.value());
		response.setContentType(CONTENT_TYPE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(MAPPER.writeValueAsString(body));
	}
}
