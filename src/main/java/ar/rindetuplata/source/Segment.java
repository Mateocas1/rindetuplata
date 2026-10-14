package ar.rindetuplata.source;

/** Which persona a product serves (S9). Only {@link #SAVER} feeds the MVP ranking; the rest are kept, not deleted. */
public enum Segment {
    SAVER,
    /** Legal entities ("PPJJ"). */
    LEGAL_ENTITY,
    /** Products in dollars. */
    FOREIGN_CURRENCY,
    /** UVA-adjusted products: their rate is a real spread over inflation, not comparable to a nominal TEA. */
    UVA_ADJUSTED
}
