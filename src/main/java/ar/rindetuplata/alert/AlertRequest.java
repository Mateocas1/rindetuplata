package ar.rindetuplata.alert;

import java.math.BigDecimal;

/**
 * The body of {@code POST /api/v1/alerts}. {@code type} is optional and defaults to
 * {@code THRESHOLD}, the original rule "avisame si alguna billetera pasa del X% anual"; {@code BEST_CHANGED},
 * {@code RATE_DROP} and {@code WEEKLY_DIGEST} are the others, and each accepts only its own fields. {@code type}
 * and {@code kind} arrive as text so an unknown one is a 400 naming the field instead of a deserialization failure
 * with no parameter, and {@code thresholdPercent} is a percent, converted to the fraction the evaluation compares
 * against.
 */
public record AlertRequest(String type, String kind, Long instrumentId, BigDecimal thresholdPercent) {
}
