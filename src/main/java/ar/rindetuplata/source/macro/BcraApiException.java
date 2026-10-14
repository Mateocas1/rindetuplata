package ar.rindetuplata.source.macro;

import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.SourceRequestException;
import java.util.List;

/** The BCRA statistics API rejected the request and said why in {@code errorMessages}. */
public class BcraApiException extends SourceRequestException {

    private final transient List<String> errorMessages;

    public BcraApiException(SourceRef source, int status, String body, List<String> errorMessages) {
        super(source, status, body);
        this.errorMessages = List.copyOf(errorMessages);
    }

    public List<String> errorMessages() {
        return errorMessages;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ": " + String.join("; ", errorMessages);
    }
}
