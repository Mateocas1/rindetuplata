package ar.rindetuplata.source;

public enum FlagCode {
    /** The rate is missing, zero or negative. */
    NON_POSITIVE_RATE,
    /** The rate is above 300%, which no source legitimately reports. */
    RATE_ABOVE_MAXIMUM,
    /** The data date is older than the window for its kind. */
    STALE,
    /** BCRA {@code plazoMinimoInvertirDias} holds a 0-5 code, not days. */
    TERM_CODE_UNDOCUMENTED,
    /** A required price or quote field is missing or not positive. */
    INVALID_QUOTE,
    /** A series observation has no value. */
    MISSING_VALUE,
    /** A mapped fund class is absent from the fund response. */
    FUND_CLASS_NOT_FOUND
}
