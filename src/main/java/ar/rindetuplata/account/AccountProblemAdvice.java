package ar.rindetuplata.account;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns an {@link AccountException} into the same RFC 9457 {@code ProblemDetail} the rest of the API uses, adding
 * {@code Retry-After} when the failure carries one.
 */
@RestControllerAdvice
public class AccountProblemAdvice {

	@ExceptionHandler(AccountException.class)
	ResponseEntity<ProblemDetail> onAccountException(AccountException exception) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
		if (exception.retryAfterSeconds() > 0) {
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
		}
		return response.body(ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage()));
	}
}
