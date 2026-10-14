package ar.rindetuplata.domain;

/**
 * Anything that can be simulated for an amount and a number of days. Deliberately open (not sealed): market-priced
 * instruments (LECAP/BONCAP, cauciones, dólar MEP) can implement it later without touching the ranking.
 */
public interface Instrument {

    String id();

    String name();

    /** Simulates the instrument, or explains why the amount/term is not eligible. */
    Evaluation evaluate(Money amount, int days);

    /** Shared input check for every instrument: the amount and the term must be positive. */
    static void requirePositiveInput(Money amount, int days) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("days must be positive");
        }
    }
}
