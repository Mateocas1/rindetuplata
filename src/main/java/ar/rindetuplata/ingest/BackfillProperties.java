package ar.rindetuplata.ingest;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.backfill.*}: the polite delay between two calls to the dated FCI endpoint of one run (a year is
 * 366 calls to a free public API, so the run spreads them out; the default 500 ms makes it a few minutes).
 */
@ConfigurationProperties("rindetuplata.backfill")
public record BackfillProperties(@DefaultValue("500ms") Duration delay) {

	public BackfillProperties {
		if (delay.isNegative()) {
			throw new IllegalArgumentException("rindetuplata.backfill.delay must not be negative: " + delay);
		}
	}
}
