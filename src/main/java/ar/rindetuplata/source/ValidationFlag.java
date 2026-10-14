package ar.rindetuplata.source;

/** A finding of the validation layer about one row. Rows are flagged, never dropped. */
public record ValidationFlag(FlagCode code, Severity severity, String message) {
}
