package ar.rindetuplata.alert;

import java.time.LocalDate;

/**
 * Runs the active alert rules for one ingestion date (T6c, S3). The evaluation reads the personalized ranking as of
 * that date and reconciles each rule's matches; it is a port so the listener that triggers it stays testable
 * without a database.
 */
public interface AlertEvaluator {

	void evaluate(LocalDate dataDate);

}
