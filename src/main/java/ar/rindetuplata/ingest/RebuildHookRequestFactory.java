package ar.rindetuplata.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * The HTTP plumbing of the rebuild hook: a JDK {@link HttpClient} with the configured timeout as its connect
 * timeout, and the same timeout as the read timeout, so a silent deploy hook can never hold the ingestion longer
 * than {@code rindetuplata.ingest.rebuild-hook-timeout}. It is a named type (instead of a raw
 * {@link JdkClientHttpRequestFactory}, which exposes no getter in Spring 7) so both timeouts stay assertable.
 */
final class RebuildHookRequestFactory implements ClientHttpRequestFactory {

	private final HttpClient httpClient;

	private final Duration readTimeout;

	private final JdkClientHttpRequestFactory delegate;

	RebuildHookRequestFactory(Duration timeout) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
		this.readTimeout = timeout;
		this.delegate = new JdkClientHttpRequestFactory(this.httpClient);
		this.delegate.setReadTimeout(timeout);
	}

	Duration connectTimeout() {
		return httpClient.connectTimeout().orElseThrow();
	}

	Duration readTimeout() {
		return readTimeout;
	}

	@Override
	public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
		return delegate.createRequest(uri, httpMethod);
	}

}
