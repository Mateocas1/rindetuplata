package ar.rindetuplata.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Exercises the real client wiring (timeouts, User-Agent) against a loopback server, never a live source. */
class SourceClientsTest {

    private static final SourceRef SOURCE = new SourceRef("loopback", "Loopback", false);

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ua", exchange -> {
            byte[] body = exchange.getRequestHeaders().getFirst("User-Agent").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException | IOException e) {
                // the client gave up first
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SourcesProperties properties(Duration readTimeout) {
        return new SourcesProperties(Duration.ofSeconds(2), readTimeout, "rindetuplata-test", baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, 24);
    }

    @Test
    void sendsTheConfiguredUserAgent() {
        RestClient client = SourceClients.create(properties(Duration.ofSeconds(2)), baseUrl);

        assertThat(new String(SourceHttp.get(client, SOURCE, "/ua"), StandardCharsets.UTF_8)).isEqualTo("rindetuplata-test");
    }

    @Test
    void readTimeoutSurfacesAsASourceException() {
        RestClient client = SourceClients.create(properties(Duration.ofMillis(200)), baseUrl);

        assertThatThrownBy(() -> SourceHttp.get(client, SOURCE, "/slow")).isInstanceOf(SourceException.class);
    }
}
