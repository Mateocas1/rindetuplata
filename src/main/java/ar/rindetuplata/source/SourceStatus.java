package ar.rindetuplata.source;

public enum SourceStatus {
    OK,
    /** At least one source of a composite failed; the result is partial. */
    DEGRADED
}
