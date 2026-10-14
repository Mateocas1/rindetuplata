package ar.rindetuplata.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A temporary checkout of the T9 {@code data} branch for the replay tests, built once per JVM from the recorded
 * 2026-10-14 archive:
 *
 * <ul>
 * <li>{@code raw/2026/10/14/} — the real archive, untouched;</li>
 * <li>{@code raw/2026/10/15/} — the same archive with {@code argentinadatos-plazo-fijo} not ok and
 * {@code uala-cuenta-remunerada} absent from the manifest;</li>
 * <li>nothing for {@code 2026-10-16} — a date with no manifest at all.</li>
 * </ul>
 */
final class TestArchives {

	static final LocalDate REAL_DAY = LocalDate.parse("2026-10-14");
	static final Instant REAL_CAPTURED_AT = Instant.parse("2026-10-14T19:06:23Z");
	static final LocalDate PARTIAL_DAY = LocalDate.parse("2026-10-15");
	static final Instant PARTIAL_CAPTURED_AT = Instant.parse("2026-10-15T19:05:00Z");
	static final LocalDate NO_MANIFEST_DAY = LocalDate.parse("2026-10-16");

	/** The archive files of the recorded day, copied into every case (the partial day rewrites its manifest). */
	private static final List<String> ARCHIVED_FILES = List.of("manifest.json", "bcra-transparencia-plazos-fijos.json",
			"argentinadatos-plazo-fijo.json", "argentinadatos-rendimientos.json", "uala-cuenta-remunerada.json");

	static final Path ROOT = build();

	private static final String PARTIAL_MANIFEST = """
			{
			  "date": "2026-10-15",
			  "captured_at": "2026-10-15T19:05:00Z",
			  "sources": [
			    {
			      "name": "bcra-transparencia-plazos-fijos",
			      "url": "https://api.bcra.gob.ar/transparencia/v1.0/PlazosFijos",
			      "status": "ok",
			      "bytes": 393501
			    },
			    {
			      "name": "argentinadatos-plazo-fijo",
			      "url": "https://api.argentinadatos.com/v1/finanzas/tasas/plazoFijo",
			      "status": "error",
			      "bytes": 20511
			    },
			    {
			      "name": "argentinadatos-rendimientos",
			      "url": "https://api.argentinadatos.com/v1/finanzas/rendimientos",
			      "status": "ok",
			      "bytes": 7227
			    }
			  ]
			}
			""";

	private TestArchives() {
	}

	/** The manifest path the job must name when it is missing. */
	static Path manifestPath(LocalDate day) {
		return ROOT.resolve("raw/%04d/%02d/%02d/manifest.json".formatted(day.getYear(), day.getMonthValue(),
				day.getDayOfMonth()));
	}

	/** The raw body of one archived source of one day. */
	static byte[] body(LocalDate day, String sourceName) {
		try {
			return Files.readAllBytes(ROOT.resolve("raw/%04d/%02d/%02d/%s.json".formatted(day.getYear(),
					day.getMonthValue(), day.getDayOfMonth(), sourceName)));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path build() {
		try {
			Path root = Files.createTempDirectory("rindetuplata-archive");
			Path real = root.resolve("raw/2026/10/14");
			Files.createDirectories(real);
			for (String name : ARCHIVED_FILES) {
				copy(name, real.resolve(name));
			}
			Path partial = root.resolve("raw/2026/10/15");
			Files.createDirectories(partial);
			for (String name : ARCHIVED_FILES) {
				copy(name, partial.resolve(name));
			}
			Files.writeString(partial.resolve("manifest.json"), PARTIAL_MANIFEST, StandardCharsets.UTF_8);
			return root;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void copy(String name, Path target) throws IOException {
		try (InputStream in = TestArchives.class.getResourceAsStream("/archive/raw/2026/10/14/" + name)) {
			if (in == null) {
				throw new IllegalStateException("missing recorded archive resource: " + name);
			}
			Files.copy(in, target);
		}
	}
}
