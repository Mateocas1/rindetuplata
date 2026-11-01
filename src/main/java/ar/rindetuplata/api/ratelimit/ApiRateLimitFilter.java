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
 * {@code /api/v1/simulations}, {@code /api/v1/instruments}, {@code /api/v1/instruments/**},
 * {@code /api/v1/entities}, {@code /api/v1/entities/**}, {@code /api/v1/reports} and {@code /api/v1/reports/**},
 * and {@code POST /mcp} (the MCP server, T6e), the same paths the security chain leaves open. It runs inside the
 * security chain, after CORS so a refused browser call still carries its CORS headers, and answers a 429
 * {@code ProblemDetail} with {@code Retry-After}, written by hand because no {@code HandlerExceptionResolver} runs
 * here. The address is Tomcat's remote address, resolved
 * from {@code X-Forwarded-For} of the trusted proxy ({@code server.forward-headers-strategy=native}).
 * A request whose {@code X-Build-Token} equals the configured bypass token is not counted; a blank configured
 * token disables the bypass, and the token is compared in constant time and never logged.
 * An {@code X-Api-Key} on these paths (T6d, S6) is judged by {@link ApiKeyAdmission}: a valid key is limited by its
 * own bucket and daily quota instead of the address, an unknown or revoked one is a 401 {@code ProblemDetail} that
 * costs one token of the address bucket (so a flood of invented keys is bounded and, once the bucket is spent, makes
 * no database lookup), and the header is never read on any other path. The key identifies a quota only: it authenticates nobody.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

	static final String BUILD_TOKEN_HEADER = "X-Build-Token";

	static final String API_KEY_HEADER = "X-Api-Key";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final RequestMatcher LIMITED = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/ranking"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/simulations"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/instruments"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/instruments/**"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/entities"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/entities/**"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/reports"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/reports/**"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/mcp"));

	private final ApiRateLimiter limiter;

	private final byte[] bypassToken;

	private final ApiKeyAdmission keys;

	public ApiRateLimitFilter(ApiRateLimiter limiter, String bypassToken) {
		this(limiter, bypassToken, null);
	}

	public ApiRateLimitFilter(ApiRateLimiter limiter, String bypassToken, ApiKeyAdmission keys) {
		this.limiter = limiter;
		this.keys = keys;
		// Trimmed like the web build trims API_BUILD_TOKEN, so a stray newline in an env file cannot silently mismatch.
		this.bypassToken = bypassToken == null || bypassToken.isBlank() ? null
				: bypassToken.strip().getBytes(StandardCharsets.UTF_8);
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
		String presentedKey = keys == null ? null : request.getHeader(API_KEY_HEADER);
		if (presentedKey != null) {
			filterKeyed(presentedKey, request, response, chain);
			return;
		}
		OptionalLong retryAfter = limiter.tryAcquire(request.getRemoteAddr());
		if (retryAfter.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		addressLimited(request, response, retryAfter.getAsLong());
	}

	/**
	 * A keyed request. Whether a key is valid is only known after the database lookup, so the address bucket is first
	 * only peeked: an address that has spent it on refused keys gets the address 429 with no lookup at all. A key
	 * that is then not valid (malformed or unknown or revoked) is charged one token of that bucket and answered 401
	 * (or the address 429 if that charge finds it empty); a valid key is never charged to the address. The price is
	 * that once an address has spent its bucket, even a valid key from it is refused until a token is back.
	 */
	private void filterKeyed(String presentedKey, HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String address = request.getRemoteAddr();
		OptionalLong spent = limiter.peek(address);
		if (spent.isPresent()) {
			addressLimited(request, response, spent.getAsLong());
			return;
		}
		switch (keys.admit(presentedKey)) {
			case ApiKeyAdmission.Admitted admitted -> chain.doFilter(request, response);
			case ApiKeyAdmission.Limited limited -> problem(request, response, HttpStatus.TOO_MANY_REQUESTS,
					limited.detail(), limited.retryAfterSeconds());
			case ApiKeyAdmission.Unknown unknown -> {
				OptionalLong charged = limiter.tryAcquire(address);
				if (charged.isPresent()) {
					addressLimited(request, response, charged.getAsLong());
				}
				else {
					problem(request, response, HttpStatus.UNAUTHORIZED, "The API key is unknown or has been revoked", 0);
				}
			}
		}
	}

	private static void addressLimited(HttpServletRequest request, HttpServletResponse response, long retryAfter)
			throws IOException {
		problem(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests from this address; try again later",
				retryAfter);
	}

	private static void problem(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String detail, long retryAfterSeconds) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", request.getRequestURI());

		response.setStatus(status.value());
		if (retryAfterSeconds > 0) {
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
		}
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
