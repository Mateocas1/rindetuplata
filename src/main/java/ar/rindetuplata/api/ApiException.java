package ar.rindetuplata.api;

import org.springframework.http.HttpStatus;

/**
 * An API-level failure that the shared advice turns into an RFC 9457 {@code ProblemDetail}. It names the offending
 * request parameter, so every 400 and 404 of the public API can say which one is wrong (T5c reuses this).
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String parameter;

	private ApiException(HttpStatus status, String parameter, String detail) {
		super(detail);
		this.status = status;
		this.parameter = parameter;
	}

	public static ApiException badRequest(String parameter, String detail) {
		return new ApiException(HttpStatus.BAD_REQUEST, parameter, detail);
	}

	public static ApiException notFound(String parameter, String detail) {
		return new ApiException(HttpStatus.NOT_FOUND, parameter, detail);
	}

	public HttpStatus status() {
		return status;
	}

	/** The request parameter the caller got wrong, or {@code null} when no single one is to blame. */
	public String parameter() {
		return parameter;
	}
}
