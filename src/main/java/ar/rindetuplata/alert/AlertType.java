package ar.rindetuplata.alert;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The kinds of alert rule (T6c, S6, L32 a). {@link #THRESHOLD} is the original rule ("avisame si algo pasa del X%
 * anual") and the default for a request without a type and for every row stored before V9.
 */
public enum AlertType {

	/** Fires when an entry of its filter crosses a TNA threshold; edge-triggered through {@code alert_match}. */
	THRESHOLD,
	/** "Cambió la mejor opción": fires when the top of its filter differs from the one stored last time. */
	BEST_CHANGED,
	/** "Mi banco bajó la tasa": fires when the TNA of one plazo fijo or wallet is below the one stored last time. */
	RATE_DROP,
	/** "Resumen semanal": at most one digest of the top of its filter per ISO week. */
	WEEKLY_DIGEST;

	/** The names the API lists when a type is unknown. */
	static final String NAMES = Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));

	/** The type for the text a client sent, ignoring case and surrounding blanks; empty for an unknown name. */
	static Optional<AlertType> parse(String text) {
		try {
			return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
