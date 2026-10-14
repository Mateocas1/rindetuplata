package ar.rindetuplata.curation;

import java.util.List;

/** An alternative schedule that applies only if a labeled condition is met, as the admin panel gives it. */
public record ConditionRequest(String condition, List<TierRequest> tiers) {
}
