package ar.rindetuplata.ranking;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The rate a ranking entry was simulated with, as a fraction. Every mapped rate is normalized to a TNA basis
 * before it reaches the simulator, so {@code basis} is always {@value #TNA} here.
 */
public record RateUsed(BigDecimal fraction, String basis, RateKind kind) {

	public static final String TNA = "TNA";

	public RateUsed {
		Objects.requireNonNull(fraction, "fraction");
		Objects.requireNonNull(basis, "basis");
		Objects.requireNonNull(kind, "kind");
	}

	public static RateUsed tna(BigDecimal fraction, RateKind kind) {
		return new RateUsed(fraction, TNA, kind);
	}
}
