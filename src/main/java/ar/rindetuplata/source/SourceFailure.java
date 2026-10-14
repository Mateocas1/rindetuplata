package ar.rindetuplata.source;

/** A source that failed inside a composite fetch. */
public record SourceFailure(SourceRef source, String reason) {
}
