package ar.rindetuplata.source;

/** Where a datum comes from: a stable id for storage, a display name for the UI and whether it is an official source. */
public record SourceRef(String id, String displayName, boolean official) {
}
