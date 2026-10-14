package ar.rindetuplata.source;

/** The source answered with an HTTP error status. */
public class SourceRequestException extends SourceException {

    private final int status;
    private final String body;

    public SourceRequestException(SourceRef source, int status, String body) {
        super(source, "HTTP " + status, null);
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
