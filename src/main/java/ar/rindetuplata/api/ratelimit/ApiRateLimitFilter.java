package ar.rindetuplata.api.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The per-address limit of the public read endpoints (T1a, S1): {@code GET /api/v1/ranking},
 * {@code /api/v1/simulations}, {@code /api/v1/instruments} and {@code /api/v1/instruments/**}, the same paths the
 * security chain leaves open. It runs inside the security chain, after CORS so a refused browser call still carries
 * its CORS headers, and answers a 429 {@code ProblemDetail} with {@code Retry-After}, written by hand because no
 * {@code HandlerExceptionResolver} runs here. The address is Tomcat's remote address, resolved from
 * {@code X-Forwarded-For} of the trusted proxy ({@code server.forward-headers-strategy=native}).
 * A request whose {@code X-Build-Token} equals the configured bypass token is not counted; a blank configured
 * token disables the bypass, and the token is compared in constant time and never logged.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

	static final String BUILD_TOKEN_HEADER = "X-Build-Token";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final RequestMatcher LIMITED = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/ranking"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/simulations"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/instruments"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/instruments/**"));

	private final ApiRateLimiter limiter;

	private final byte[] bypassToken;

	public ApiRateLimitFilter(ApiRateLimiter limiter, String bypassToken) {
		this.limiter = limiter;
		this.bypassToken = bypassToken == null || bypassToken.isBlank() ? null
				: bypassToken.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !LIMITED.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (bypasses(request)) {
			chain.doFilter(request, response);
			return;
		}
		OptionalLong retryAfter = limiter.tryAcquire(request.getRemoteAddr());
		if (retryAfter.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", "Too many requests from this address; try again later");
		body.put("instance", request.getRequestURI());

		response.setStatus(status.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.getAsLong()));
		response.setContentType("application/problem+json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(MAPPER.writeValueAsString(body));
	}

	private boolean bypasses(HttpServletRequest request) {
		if (bypassToken == null) {
			return false;
		}
		String presented = request.getHeader(BUILD_TOKEN_HEADER);
		return presented != null && MessageDigest.isEqual(bypassToken, presented.getBytes(StandardCharsets.UTF_8));
	}
}
