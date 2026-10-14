package ar.rindetuplata.domain;

/** Best final amount reachable only if a labeled condition is met (reported as "up to"). */
public record ConditionalOutcome(String condition, Money finalAmount) {
}
