package ar.rindetuplata.api;

import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The shared error handling of the public API: failures become RFC 9457 {@code ProblemDetail} bodies that name the
 * offending parameter in {@code properties.parameter}. Spring flattens the problem's properties onto the body, so
 * the parameter arrives as a top-level {@code parameter} field. It handles this package's {@link ApiException} (an
 * invalid value or an unknown resource) and Spring's own binding failures, so T5c gets the same contract for free.
 */
@RestControllerAdvice
public class ApiProblemAdvice {

	public static final String PARAMETER_PROPERTY = "parameter";

	@ExceptionHandler(ApiException.class)
	ProblemDetail onApiException(ApiException exception) {
		return problem(exception.status(), exception.parameter(), exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException exception) {
		String detail = "Parameter '" + exception.getName() + "' has an invalid value: '" + exception.getValue() + "'";
		Class<?> required = exception.getRequiredType();
		if (required != null && required.isEnum()) {
			detail += "; allowed values are " + Arrays.toString(required.getEnumConstants());
		}
		return problem(HttpStatus.BAD_REQUEST, exception.getName(), detail);
	}

	private static ProblemDetail problem(HttpStatus status, String parameter, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		if (parameter != null) {
			problem.setProperty(PARAMETER_PROPERTY, parameter);
		}
		return problem;
	}
}
