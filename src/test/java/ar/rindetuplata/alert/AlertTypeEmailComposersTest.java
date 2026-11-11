package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.MailMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * One composer per alert type, each in Spanish in the voice of today's email (voseo, plain text, the
 * link to manage the alerts and the informational disclaimer). The events are the recorded payloads of
 * {@code fixtures/alert/}, so the test also pins the payload shape the evaluation writes and the consumer reads.
 */
class AlertTypeEmailComposersTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final String MANAGE_URL = "https://app.rindetuplata.ar/alertas";

	private static final AlertMailProperties PROPERTIES = new AlertMailProperties(MANAGE_URL);

	private static final String FOOTER = """

			Regla #%d. Podés ver el detalle y administrar tus alertas acá:
			""" + MANAGE_URL + """


			rindetuplata compara rendimientos con datos públicos y no da asesoramiento financiero.
			""";

	@Test
	void bestChangedNamesTheNewTopThePreviousOneAndTheSource() throws Exception {
		BestChangedEvent event = read(AlertFixtures.BEST_CHANGED, BestChangedEvent.class);

		MailMessage mail = new BestChangedEmailComposer(PROPERTIES).compose(event);

		assertThat(mail.to()).isEqualTo("saver@example.com");
		assertThat(mail.subject()).isEqualTo("rindetuplata: cambió la mejor opción");
		assertThat(mail.body()).isEqualTo("""
				Hola,

				Cambió la mejor opción entre las billeteras: ahora es Wallet Nueva, con 35.12% TNA.
				Antes era Wallet Vieja (según los datos del 2026-10-14).
				Rendimiento real a 30 días: 0.41%.

				Datos del 2026-10-15 · Fuente: test-source
				""" + FOOTER.formatted(31));
	}

	@Test
	void bestChangedWithoutKindOrRealReturnSaysAmongEveryOptionAndDropsTheRealLine() throws Exception {
		BestChangedEvent event = read(AlertFixtures.BEST_CHANGED, BestChangedEvent.class);
		BestChangedEvent open = new BestChangedEvent(event.type(), event.eventId(), event.ruleId(), event.userEmail(),
				null, event.instrumentId(), event.instrumentName(), "FUND", event.rate(), null, event.captureDate(),
				event.dataDate(), event.sourceDisplayName(), event.previousName(), event.previousObservedOn());

		String body = new BestChangedEmailComposer(PROPERTIES).compose(open).body();

		assertThat(body).contains("Cambió la mejor opción entre todas las opciones: ahora es Wallet Nueva, "
				+ "con 35.12% TNA equivalente.");
		assertThat(body).doesNotContain("Rendimiento real");
	}

	@Test
	void rateDropShowsBothRatesAndWhenTheOldOneWasSeen() throws Exception {
		RateDroppedEvent event = read(AlertFixtures.RATE_DROP, RateDroppedEvent.class);

		MailMessage mail = new RateDropEmailComposer(PROPERTIES).compose(event);

		assertThat(mail.to()).isEqualTo("saver@example.com");
		assertThat(mail.subject()).isEqualTo("rindetuplata: bajó una tasa que seguís");
		assertThat(mail.body()).isEqualTo("""
				Hola,

				Bajó la tasa de Banco Prueba: pasó de 30% a 27.5% TNA.
				La tasa anterior la vimos el 2026-10-14.

				Datos del 2026-10-15 · Fuente: test-source
				""" + FOOTER.formatted(32));
	}

	@Test
	void weeklyDigestListsEveryEntryInOrderAndTheChangeOfTop() throws Exception {
		WeeklyDigestEvent event = read(AlertFixtures.WEEKLY_DIGEST, WeeklyDigestEvent.class);

		MailMessage mail = new WeeklyDigestEmailComposer(PROPERTIES).compose(event);

		assertThat(mail.to()).isEqualTo("saver@example.com");
		assertThat(mail.subject()).isEqualTo("rindetuplata: tu resumen semanal");
		assertThat(mail.body()).isEqualTo("""
				Hola,

				Este es tu resumen de la semana del 2026-10-19. Las 2 mejores opciones entre todas las opciones, en orden de ranking:

				1. Wallet Nueva · 35% TNA · real a 30 días: 0.41% · Fuente: test-source · dato del 2026-10-21
				2. Fondo Prueba · 30% TNA equivalente · real a 30 días: sin dato · Fuente: fuente-fondos · dato del 2026-10-20

				La mejor opción cambió desde tu resumen anterior (semana del 2026-10-12): antes era Wallet Vieja, ahora es Wallet Nueva.
				""" + FOOTER.formatted(33));
	}

	@Test
	void weeklyDigestOfOneKindWithOneEntryAndNoChangeIsShorter() throws Exception {
		WeeklyDigestEvent event = read(AlertFixtures.WEEKLY_DIGEST, WeeklyDigestEvent.class);
		WeeklyDigestEvent single = new WeeklyDigestEvent(event.type(), event.eventId(), event.ruleId(),
				event.userEmail(), "FUND", event.weekStart(), event.dataDate(), event.entries().subList(1, 2), null);

		String body = new WeeklyDigestEmailComposer(PROPERTIES).compose(single).body();

		assertThat(body).contains("La mejor opción entre los fondos:")
			.contains("1. Fondo Prueba · 30% TNA equivalente")
			.doesNotContain("La mejor opción cambió");
	}

	private static <T> T read(String fixture, Class<T> type) {
		return MAPPER.readValue(AlertFixtures.read(fixture), type);
	}

}
