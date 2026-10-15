package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T7a alert-email rules (S13, S8 L-G): the rate a saver reads is a percent rounded to two decimals {@code HALF_EVEN}
 * (never a long fraction), its label says {@code TNA} for a plazo fijo or a wallet and {@code TNA equivalente} for a
 * fund or crypto yield, and the rest of the Spanish copy stays as T6d wrote it.
 */
class AlertEmailComposerTest {

	private final AlertEmailComposer composer = new AlertEmailComposer(
			new AlertMailProperties("https://app.rindetuplata.ar/alertas"));

	@Test
	void percentIsRoundedToTwoDecimalsHalfEven() {
		assertThat(AlertEmailComposer.percent(new BigDecimal("0.287549"))).isEqualTo("28.75%");
		assertThat(AlertEmailComposer.percent(new BigDecimal("0.28755"))).as("28.755 rounds up to even 28.76")
			.isEqualTo("28.76%");
		assertThat(AlertEmailComposer.percent(new BigDecimal("0.28745"))).as("28.745 rounds down to even 28.74")
			.isEqualTo("28.74%");
		assertThat(AlertEmailComposer.percent(new BigDecimal("0.30"))).as("trailing zeros are dropped")
			.isEqualTo("30%");
	}

	@Test
	void theRateLabelIsTnaForPlazoFijoAndWallet() {
		assertThat(body("PLAZO_FIJO")).contains("está rindiendo 28.75% TNA, por encima de tu umbral de 25%");
		assertThat(body("WALLET")).contains("está rindiendo 28.75% TNA, por encima de tu umbral de 25%");
	}

	@Test
	void theRateLabelIsTnaEquivalenteForFundAndCrypto() {
		assertThat(body("FUND")).contains("está rindiendo 28.75% TNA equivalente, por encima de tu umbral de 25%");
		assertThat(body("CRYPTO")).contains("está rindiendo 28.75% TNA equivalente, por encima de tu umbral de 25%");
	}

	@Test
	void theRestOfTheSpanishCopyIsUnchanged() {
		assertThat(body("WALLET"))
			.contains("Se disparó tu alerta:")
			.contains("Datos del 2026-10-14 · Fuente: test-source")
			.contains("Regla #12")
			.contains("https://app.rindetuplata.ar/alertas")
			.contains("no da asesoramiento financiero");
	}

	private String body(String kind) {
		AlertTriggered event = new AlertTriggered(UUID.randomUUID(), 12, "saver@example.com", "42", "Wallet Test",
				kind, new BigDecimal("0.287549"), new BigDecimal("0.25"), LocalDate.parse("2026-10-14"),
				LocalDate.parse("2026-10-14"), "test-source");
		return composer.compose(event).body();
	}

}
