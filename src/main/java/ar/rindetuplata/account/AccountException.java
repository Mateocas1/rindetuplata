package ar.rindetuplata.account;

import org.springframework.http.HttpStatus;

/**
 * An account failure the API turns into an RFC 9457 {@code ProblemDetail}: T6a uses it for a magic link that is
 * unknown, already used or expired (401). Values the caller got wrong are {@code ar.rindetuplata.api.ApiException}
 * 400s, so both failures share the same error contract.
 */
public class AccountException extends RuntimeException {

	private final HttpStatus status;

	private AccountException(HttpStatus status, String detail) {
		super(detail);
		this.status = status;
	}

	public static AccountException unauthorized(String detail) {
		return new AccountException(HttpStatus.UNAUTHORIZED, detail);
	}

	public HttpStatus status() {
		return status;
	}
}
