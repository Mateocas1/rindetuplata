package ar.rindetuplata.alert;

import java.math.BigDecimal;

/**
 * The body of {@code POST /api/v1/alerts} (T6c, S3): "avisame si alguna billetera pasa del X% anual". Every field
 * is optional except the threshold; {@code kind} arrives as text so an unknown one is a 400 naming the field
 * instead of a deserialization failure with no parameter, and {@code thresholdPercent} is a percent, converted to
 * the fraction the evaluation compares against.
 */
public record AlertRequest(String kind, Long instrumentId, BigDecimal thresholdPercent) {
}
