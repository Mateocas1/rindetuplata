package ar.rindetuplata.ingest;

import java.time.Duration;

/** The wait between two calls to a source during a backfill; a seam so tests record the delay instead of sleeping. */
@FunctionalInterface
public interface BackfillPause {

	void pause(Duration delay) throws InterruptedException;
}
