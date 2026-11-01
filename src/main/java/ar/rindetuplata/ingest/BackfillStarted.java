package ar.rindetuplata.ingest;

import java.time.LocalDate;

/** What the admin endpoint answers with 202: the id of the job execution that now runs {@code from..to}. */
public record BackfillStarted(long jobId, LocalDate from, LocalDate to) {
}
