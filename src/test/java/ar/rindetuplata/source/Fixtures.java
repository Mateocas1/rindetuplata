package ar.rindetuplata.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads the recorded fixtures under {@code src/test/resources/fixtures/}. */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] bytes(String path) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String text(String path) {
        return new String(bytes(path), StandardCharsets.UTF_8);
    }
}
