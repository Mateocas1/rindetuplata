package ar.rindetuplata.ingest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the rebuild hook (T4d): the {@link RestClient} that POSTs to the static site's deploy hook, with
 * {@code rindetuplata.ingest.rebuild-hook-timeout} on both connect and read, plus the listener
 * {@code dailyIngestionJob} registers.
 */
@Configuration(proxyBeanMethods = false)
class RebuildHookConfiguration {

	@Bean
	RestClient rebuildHookRestClient(IngestionProperties properties) {
		return RestClient.builder()
			.requestFactory(new RebuildHookRequestFactory(properties.rebuildHookTimeout()))
			.build();
	}

	@Bean
	RebuildHookListener rebuildHookListener(RestClient rebuildHookRestClient, IngestionProperties properties) {
		return new RebuildHookListener(rebuildHookRestClient, properties.rebuildHookUrl());
	}

}
