package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.account.MailMessage;
import ar.rindetuplata.account.RecordingMailer;
import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletTier;
import ar.rindetuplata.store.InstrumentKind;
import ar.rindetuplata.store.StoreReader;
import ar.rindetuplata.store.WalletRateWriter;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup of the {@code alert} API tests: the whole application over a Testcontainers Postgres, the scheduler
 * disabled, a recording mailer and a real magic-link login through the HTTP stack, so a rule is always created by
 * an authenticated account and ownership is proven by two real users. One rolled-back transaction per test keeps
 * the seeded instruments and rules from leaking.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=https://app.rindetuplata.ar/ingresar",
		"rindetuplata.auth.admin-emails=admin@rindetuplata.ar",
		"rindetuplata.web.allowed-origins=http://localhost:4321"
})
@AutoConfigureMockMvc
@Import(AccountTestConfiguration.class)
@Transactional
abstract class AlertApiIntegrationTest {

	static final String USER_EMAIL = "saver@example.com";
	static final String OTHER_EMAIL = "otro@example.com";
	static final LocalDate CAPTURE = LocalDate.parse("2026-10-14");
	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");

	static final int MAX_ACTIVE_RULES = 20;

	private static final String SESSION_COOKIE = "SESSION";

	/** The 36-character value Spring Security's one-time-token service puts in the link. */
	private static final Pattern TOKEN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected RecordingMailer mailer;

	@Autowired
	protected StoreReader reader;

	@Autowired
	protected WalletRateWriter walletWriter;

	@BeforeEach
	void clearMailbox() {
		mailer.clear();
	}

	/** Requests a magic link, logs in with the mailed token and returns the session cookie of that account. */
	protected Cookie login(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted());
		MailMessage message = mailer.last();
		assertThat(message).as("a magic-link email was recorded").isNotNull();
		Matcher matcher = TOKEN.matcher(message.body());
		assertThat(matcher.find()).as("the email body carries a token (%s)", message.body()).isTrue();

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + matcher.group(1) + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		for (Cookie cookie : result.getResponse().getCookies()) {
			if (SESSION_COOKIE.equals(cookie.getName())) {
				return cookie;
			}
		}
		throw new AssertionError("the login response set no session cookie");
	}

	protected ResultActions postAlert(Cookie session, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/alerts").with(csrf())
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	protected ResultActions listAlerts(Cookie session) throws Exception {
		return mockMvc.perform(get("/api/v1/alerts").cookie(session));
	}

	protected ResultActions deleteAlert(Cookie session, long id) throws Exception {
		return mockMvc.perform(delete("/api/v1/alerts/" + id).with(csrf()).cookie(session));
	}

	/**
	 * One wallet of its own source ({@code test-*} ids never collide with a real adapter), so the ranking used by
	 * the evaluator sees exactly the rows the test seeded.
	 */
	protected long seedWallet(String sourceId, String walletId, String name, String tna, LocalDate captureDate) {
		WalletRate rate = new WalletRate(new SourceRef(sourceId, "Test " + sourceId, false), walletId, name,
				List.of(new WalletTier(BigDecimal.ZERO, null, new BigDecimal(tna))), List.of(), captureDate, null,
				FETCHED, Confidence.SCRAPED_ENTITY_WEB, List.of());
		walletWriter.write(List.of(rate), captureDate);
		return reader.instrument(InstrumentKind.WALLET, sourceId, walletId).orElseThrow().id();
	}

}
