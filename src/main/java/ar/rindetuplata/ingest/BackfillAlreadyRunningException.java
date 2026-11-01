package ar.rindetuplata.ingest;

/** A history backfill is already running in this instance: only one runs at a time. */
public class BackfillAlreadyRunningException extends RuntimeException {

	public BackfillAlreadyRunningException() {
		super("A history backfill is already running; wait for it to finish before starting another");
	}
}
