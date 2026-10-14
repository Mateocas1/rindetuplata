package ar.rindetuplata.ingest;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.ingest.*}: whether the daily scheduler runs, when and in which zone it fires, where the
 * T9 raw archive of the {@code data} branch is checked out for replays, and the static-site rebuild hook.
 */
@ConfigurationProperties("rindetuplata.ingest")
public record IngestionProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("0 30 19 * * *") String cron,
		@DefaultValue("America/Argentina/Buenos_Aires") String zone,
		@DefaultValue("archive") String archiveDir,
		@DefaultValue("") String rebuildHookUrl,
		@DefaultValue("10s") Duration rebuildHookTimeout) {

	/** The zone the scheduler uses to decide which day "today" is (S17: the capture runs from Argentina). */
	public ZoneId zoneId() {
		return ZoneId.of(zone);
	}

	/** A checkout of the T9 {@code data} branch; {@code raw/yyyy/MM/dd/manifest.json} lives under it. */
	public Path archiveRoot() {
		return Path.of(archiveDir);
	}
}
