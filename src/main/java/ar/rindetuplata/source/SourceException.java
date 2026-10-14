package ar.rindetuplata.source;

/** A source could not be fetched or its body could not be understood. */
public class SourceException extends RuntimeException {

    private final transient SourceRef source;

    public SourceException(SourceRef source, String message, Throwable cause) {
        super(source.displayName() + " (" + source.id() + "): " + message, cause);
        this.source = source;
    }

    public SourceRef source() {
        return source;
    }
}
