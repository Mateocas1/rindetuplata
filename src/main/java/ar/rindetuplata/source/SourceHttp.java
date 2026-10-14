package ar.rindetuplata.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The fetch half of every adapter: one GET returning raw bytes, with failures mapped to typed exceptions. */
public final class SourceHttp {

    private SourceHttp() {
    }

    public static byte[] get(RestClient client, SourceRef source, String uri) {
        try {
            byte[] body = client.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new SourceRequestException(source, response.getStatusCode().value(), readBody(response.getBody()));
                    })
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new SourceException(source, "empty response body", null);
            }
            return body;
        } catch (RestClientException e) {
            throw new SourceException(source, "request failed: " + e.getMessage(), e);
        }
    }

    private static String readBody(java.io.InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
