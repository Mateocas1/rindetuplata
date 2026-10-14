package ar.rindetuplata.source;

public enum Severity {
    /** Shown to the user, still usable. */
    WARNING,
    /** Kept for audit, excluded from ranking downstream. */
    INVALID
}
