package ar.rindetuplata.api;

import java.time.LocalDate;

/**
 * The calendar the public API and the MCP tools accept for a date parameter: {@code 0001-01-01} to
 * {@code 9999-12-31}, the documented {@code yyyy-MM-dd} form. {@link LocalDate} parses years up to
 * {@code +999999999}, but PostgreSQL's {@code date} stops at year 5874897 and the fund series arithmetic overflows
 * long before, so a date outside the range is rejected before any work instead of failing as a 500.
 */
public final class SupportedDates {

	public static final LocalDate MIN = LocalDate.of(1, 1, 1);

	public static final LocalDate MAX = LocalDate.of(9999, 12, 31);

	/** The range as a message fragment, shared by the REST and the MCP errors. */
	public static final String RANGE = "a date between " + MIN + " and " + MAX;

	private SupportedDates() {
	}

	public static boolean contains(LocalDate date) {
		return !date.isBefore(MIN) && !date.isAfter(MAX);
	}

	/** The date itself, or {@code null} when absent; an unsupported date is a 400 naming {@code parameter}. */
	public static LocalDate checked(String parameter, LocalDate date) {
		if (date != null && !contains(date)) {
			throw ApiException.badRequest(parameter,
					"Parameter '" + parameter + "' must be " + RANGE + ": " + date);
		}
		return date;
	}
}
