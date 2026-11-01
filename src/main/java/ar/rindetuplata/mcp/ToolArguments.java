package ar.rindetuplata.mcp;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The arguments of one tool call, validated the way the REST API validates the same query parameters
 * ({@code RankingRequest}, which is package-private to {@code api.ranking}): every bound comes from the public
 * {@link RankingQuery} constants and a rejected value is a {@link ToolArgumentException} naming the argument.
 * {@code McpArgumentEquivalenceApiTest} sends every boundary to both and keeps the two from drifting. Numbers arrive
 * from JSON as any {@link Number} or as a numeric string, which a model sometimes sends; an argument the tool does
 * not declare is refused, so a misspelled one is never silently ignored.
 */
final class ToolArguments {

	/** The longest value echoed back in a message, so a hostile argument cannot inflate the answer. */
	private static final int MAX_ECHO = 40;

	private final Map<String, Object> values;

	ToolArguments(Map<String, Object> values, Set<String> allowed) {
		this.values = values == null ? Map.of() : values;
		for (String name : new TreeSet<>(this.values.keySet())) {
			if (!allowed.contains(name)) {
				throw ToolArgumentException.invalid(name,
						"is not an argument of this tool; the allowed ones are " + new TreeSet<>(allowed));
			}
		}
	}

	Money amount() {
		Object raw = values.get("amount");
		if (raw == null) {
			return RankingQuery.DEFAULT_AMOUNT;
		}
		BigDecimal amount = decimal("amount", raw);
		if (amount.signum() <= 0) {
			throw ToolArgumentException.invalid("amount", "must be greater than 0: " + echo(amount));
		}
		if (amount.compareTo(RankingQuery.MAX_AMOUNT) > 0) {
			throw ToolArgumentException.invalid("amount",
					"must be at most " + RankingQuery.MAX_AMOUNT.toPlainString() + ": " + echo(amount));
		}
		return Money.ars(amount);
	}

	int days() {
		Object raw = values.get("days");
		if (raw == null) {
			return RankingQuery.DEFAULT_DAYS;
		}
		long days = integer("days", raw);
		if (days < 1 || days > RankingQuery.MAX_DAYS) {
			throw ToolArgumentException.invalid("days",
					"must be between 1 and " + RankingQuery.MAX_DAYS + ": " + days);
		}
		return (int) days;
	}

	int limit() {
		Object raw = values.get("limit");
		if (raw == null) {
			return RankingQuery.DEFAULT_LIMIT;
		}
		long limit = integer("limit", raw);
		if (limit < 1 || limit > RankingQuery.MAX_LIMIT) {
			throw ToolArgumentException.invalid("limit",
					"must be between 1 and " + RankingQuery.MAX_LIMIT + ": " + limit);
		}
		return (int) limit;
	}

	/** The kinds to rank; none means every kind. Names are the enum constants, case-sensitive like the REST API. */
	Set<RankedKind> kinds() {
		Set<RankedKind> kinds = new LinkedHashSet<>();
		for (Object element : list("kind")) {
			String name = element instanceof String text ? text.strip() : String.valueOf(element);
			try {
				kinds.add(RankedKind.valueOf(name));
			}
			catch (IllegalArgumentException e) {
				throw ToolArgumentException.invalid("kind",
						"has an invalid value: '" + echo(name) + "'; allowed values are "
								+ Arrays.toString(RankedKind.values()));
			}
		}
		return kinds;
	}

	Set<Long> clientOf() {
		Set<Long> ids = new LinkedHashSet<>();
		for (Object element : list("clientOf")) {
			ids.add(integer("clientOf", element));
		}
		return ids;
	}

	LocalDate date(String name) {
		Object raw = values.get(name);
		if (raw == null) {
			return null;
		}
		try {
			return LocalDate.parse(raw instanceof String text ? text.strip() : String.valueOf(raw));
		}
		catch (DateTimeParseException e) {
			throw ToolArgumentException.invalid(name, "must be a date as yyyy-MM-dd: '" + echo(raw) + "'");
		}
	}

	boolean flag(String name, boolean fallback) {
		Object raw = values.get(name);
		if (raw == null) {
			return fallback;
		}
		if (raw instanceof Boolean flag) {
			return flag;
		}
		if (raw instanceof String text && (text.equals("true") || text.equals("false"))) {
			return Boolean.parseBoolean(text);
		}
		throw ToolArgumentException.invalid(name, "must be true or false: '" + echo(raw) + "'");
	}

	long requiredId(String name) {
		Object raw = values.get(name);
		if (raw == null) {
			throw ToolArgumentException.invalid(name, "is required");
		}
		return integer(name, raw);
	}

	String requiredText(String name) {
		Object raw = values.get(name);
		if (raw == null) {
			throw ToolArgumentException.invalid(name, "is required");
		}
		if (!(raw instanceof String text)) {
			throw ToolArgumentException.invalid(name, "must be a string: '" + echo(raw) + "'");
		}
		return text.strip();
	}

	private List<Object> list(String name) {
		Object raw = values.get(name);
		if (raw == null) {
			return List.of();
		}
		if (raw instanceof Collection<?> collection) {
			return new ArrayList<>(collection);
		}
		return List.of(raw);
	}

	private static BigDecimal decimal(String name, Object raw) {
		try {
			if (raw instanceof Double || raw instanceof Float) {
				double value = ((Number) raw).doubleValue();
				if (Double.isNaN(value) || Double.isInfinite(value)) {
					throw new NumberFormatException();
				}
			}
			if (raw instanceof Number || raw instanceof String) {
				return new BigDecimal(raw instanceof String text ? text.strip() : raw.toString());
			}
		}
		catch (NumberFormatException e) {
			// falls through to the message below
		}
		throw ToolArgumentException.invalid(name, "must be a number: '" + echo(raw) + "'");
	}

	private static long integer(String name, Object raw) {
		BigDecimal value = decimal(name, raw).stripTrailingZeros();
		if (value.scale() > 0) {
			throw ToolArgumentException.invalid(name, "must be an integer: " + echo(raw));
		}
		try {
			return value.longValueExact();
		}
		catch (ArithmeticException e) {
			throw ToolArgumentException.invalid(name, "is out of range: " + echo(value));
		}
	}

	/**
	 * The rejected value as the caller can read it, cut short. A decimal such as {@code 1e999999999} parses as a
	 * valid {@code BigDecimal} whose plain form is a billion characters, so it is echoed in scientific notation.
	 */
	private static String echo(Object value) {
		String text = value instanceof BigDecimal decimal
				? (decimal.precision() - decimal.scale() > MAX_ECHO || decimal.scale() > MAX_ECHO ? decimal.toString()
						: decimal.toPlainString())
				: String.valueOf(value);
		return text.length() <= MAX_ECHO ? text : text.substring(0, MAX_ECHO) + "...";
	}
}
