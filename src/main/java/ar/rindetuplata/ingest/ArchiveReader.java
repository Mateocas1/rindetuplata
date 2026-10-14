package ar.rindetuplata.ingest;

import ar.rindetuplata.source.JsonSupport;
import ar.rindetuplata.source.SourceRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads the T9 {@code data} branch checkout: {@code raw/yyyy/MM/dd/manifest.json} plus one {@code <source>.json}
 * body per archived source. The manifest carries the capture instant every replayed row is stamped with and the
 * status of each source, so a source that is not {@code ok} is not replayed (S17).
 */
@Component
public class ArchiveReader {

	static final SourceRef ARCHIVE = new SourceRef("t9-raw-archive", "T9 raw capture archive", false);

	private final IngestionProperties properties;

	public ArchiveReader(IngestionProperties properties) {
		this.properties = properties;
	}

	/** One day's manifest: when the capture ran and how each source ended. */
	public record Manifest(Instant capturedAt, Map<String, String> statusBySource) {

		public Manifest {
			statusBySource = Map.copyOf(statusBySource);
		}

		/** Whether the archive holds a usable capture of this source. */
		public boolean ok(String sourceId) {
			return "ok".equals(statusBySource.get(sourceId));
		}
	}

	/**
	 * @throws ArchiveException when the day has no manifest, naming its absolute path
	 */
	public Manifest load(LocalDate day) {
		Path path = manifestPath(day);
		if (!Files.isRegularFile(path)) {
			throw new ArchiveException("no raw capture manifest at " + path.toAbsolutePath());
		}
		byte[] body;
		try {
			body = Files.readAllBytes(path);
		}
		catch (IOException e) {
			throw new ArchiveException("cannot read the raw capture manifest " + path.toAbsolutePath(), e);
		}
		JsonNode root = JsonSupport.readTree(ARCHIVE, body);
		String capturedAt = JsonSupport.text(root, "captured_at");
		if (capturedAt == null) {
			throw new ArchiveException("manifest " + path.toAbsolutePath() + " has no captured_at");
		}
		Map<String, String> statuses = new LinkedHashMap<>();
		for (JsonNode source : JsonSupport.array(ARCHIVE, root, "sources")) {
			String name = JsonSupport.text(source, "name");
			if (name != null) {
				statuses.put(name, JsonSupport.text(source, "status"));
			}
		}
		try {
			return new Manifest(Instant.parse(capturedAt), statuses);
		}
		catch (java.time.format.DateTimeParseException e) {
			throw new ArchiveException("manifest " + path.toAbsolutePath() + " has a bad captured_at: " + capturedAt, e);
		}
	}

	/** The raw body of one archived source. */
	public byte[] body(LocalDate day, String sourceId) {
		Path path = dayDirectory(day).resolve(sourceId + ".json");
		try {
			return Files.readAllBytes(path);
		}
		catch (IOException e) {
			throw new ArchiveException("cannot read the archived capture " + path.toAbsolutePath(), e);
		}
	}

	public Path manifestPath(LocalDate day) {
		return dayDirectory(day).resolve("manifest.json");
	}

	private Path dayDirectory(LocalDate day) {
		return properties.archiveRoot()
			.resolve("raw/%04d/%02d/%02d".formatted(day.getYear(), day.getMonthValue(), day.getDayOfMonth()));
	}
}
