package ar.rindetuplata.report;

/** The month cannot be reported: it is not closed, INDEC has not published it, or the stored history does not cover it. */
public class MonthlyReportUnavailableException extends RuntimeException {

	public enum Reason {
		NOT_CLOSED, NO_INFLATION, NO_COVERAGE
	}

	private final Reason reason;

	public MonthlyReportUnavailableException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
