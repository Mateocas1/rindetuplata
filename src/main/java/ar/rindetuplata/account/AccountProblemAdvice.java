package ar.rindetuplata.account;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns an {@link AccountException} into the same RFC 9457 {@code ProblemDetail} the rest of the API uses. */
@RestControllerAdvice
public class AccountProblemAdvice {

	@ExceptionHandler(AccountException.class)
	ProblemDetail onAccountException(AccountException exception) {
		return ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
	}
}
