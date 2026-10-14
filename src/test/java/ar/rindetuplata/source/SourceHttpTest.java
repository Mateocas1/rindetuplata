package ar.rindetuplata.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SourceHttpTest {

    private static final SourceRef SOURCE = new SourceRef("test", "Test source", false);

    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://source.test/api");
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @Test
    void returnsTheBodyBytesOfASuccessfulResponse() {
        server.expect(requestTo("https://source.test/api/data"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertThat(new String(SourceHttp.get(client, SOURCE, "/data"), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void clientErrorBecomesATypedRequestException() {
        server.expect(requestTo("https://source.test/api/data"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("bad id").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> SourceHttp.get(client, SOURCE, "/data"))
                .isInstanceOfSatisfying(SourceRequestException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.body()).isEqualTo("bad id");
                    assertThat(e.source()).isEqualTo(SOURCE);
                });
    }

    @Test
    void serverErrorBecomesATypedRequestException() {
        server.expect(requestTo("https://source.test/api/data")).andRespond(withServerError());

        assertThatThrownBy(() -> SourceHttp.get(client, SOURCE, "/data"))
                .isInstanceOfSatisfying(SourceRequestException.class, e -> assertThat(e.status()).isEqualTo(500));
    }

    @Test
    void timeoutBecomesASourceException() {
        server.expect(requestTo("https://source.test/api/data"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> SourceHttp.get(client, SOURCE, "/data"))
                .isInstanceOf(SourceException.class)
                .isNotInstanceOf(SourceRequestException.class)
                .hasMessageContaining("Test source")
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void emptyBodyBecomesASourceException() {
        server.expect(requestTo("https://source.test/api/data")).andRespond(withSuccess());

        assertThatThrownBy(() -> SourceHttp.get(client, SOURCE, "/data"))
                .isInstanceOf(SourceException.class)
                .hasMessageContaining("empty");
    }
}
