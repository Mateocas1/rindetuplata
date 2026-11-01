package ar.rindetuplata.ingest.health;

import java.time.Instant;
import java.time.LocalDate;

/** The newest capture date with a usable LIVE {@code source_run}, and when that row was recorded. */
record LastIngestionSuccess(LocalDate captureDate, Instant recordedAt) {
}
