package ar.rindetuplata.account;

import org.springframework.http.HttpStatus;

/**
 * An account failure the API turns into an RFC 9457 {@code ProblemDetail}: T6a uses it for a magic link that is
 * unknown, already used or expired (401) and for a remote address over its magic-link rate limit (429). Values the caller got wrong are {@code ar.rindetuplata.api.ApiException}
 * 400s, so both failures share the same error contract.
 */
public class AccountException extends RuntimeException {

	private final HttpStatus status;

	private final long retryAfterSeconds;

	private AccountException(HttpStatus status, String detail, long retryAfterSeconds) {
		super(detail);
		this.status = status;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public static AccountException unauthorized(String detail) {
		return new AccountException(HttpStatus.UNAUTHORIZED, detail, 0);
	}

	/** A 429 whose {@code Retry-After} header tells the client how many seconds to wait. */
	public static AccountException tooManyRequests(String detail, long retryAfterSeconds) {
		return new AccountException(HttpStatus.TOO_MANY_REQUESTS, detail, retryAfterSeconds);
	}

	public HttpStatus status() {
		return status;
	}

	/** Seconds for the {@code Retry-After} header, or 0 when the failure carries none. */
	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
