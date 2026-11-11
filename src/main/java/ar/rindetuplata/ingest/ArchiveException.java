package ar.rindetuplata.ingest;

/** The raw archive is unusable for a replay: no manifest, an unreadable body, a corrupt manifest. */
public class ArchiveException extends RuntimeException {

	public ArchiveException(String message) {
		super(message);
	}

	public ArchiveException(String message, Throwable cause) {
		super(message, cause);
	}
}
