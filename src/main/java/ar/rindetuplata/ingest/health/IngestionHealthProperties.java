package ar.rindetuplata.ingest.health;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.ingest.stale-after}: how long after the expected daily run the newest successful ingestion
 * still counts as fresh (L33b). It lives next to, not inside, the scheduler's {@code IngestionProperties}: the two
 * records bind the same prefix and each ignores the other's keys.
 */
@ConfigurationProperties("rindetuplata.ingest")
public record IngestionHealthProperties(@DefaultValue("36h") Duration staleAfter) {
}
