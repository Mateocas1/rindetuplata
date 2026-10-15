package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup of the T11a concurrency tests: the same application context as {@link AccountIntegrationTest}
 * (so it is cached and shared) but with no wrapping transaction, because several request threads must really commit
 * and see each other's rows. Every email of these tests ends in {@link #DOMAIN} and is deleted after each test.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=" + AccountIntegrationTest.MAGIC_LINK_URL,
		"rindetuplata.auth.admin-emails=" + AccountIntegrationTest.ADMIN_EMAIL,
		"rindetuplata.web.allowed-origins=" + AccountIntegrationTest.ALLOWED_ORIGIN
})
@AutoConfigureMockMvc
@Import(AccountTestConfiguration.class)
abstract class AccountConcurrencyTest {

	static final String DOMAIN = "@race.example";

	private static final Pattern TOKEN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected RecordingMailer mailer;

	@Autowired
	protected JdbcClient jdbc;

	@BeforeEach
	void resetMailbox() throws Exception {
		mailer.clear();
		// The first csrf() post-processor installs its test repository on the shared filter; do it before threads.
		mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"warmup" + DOMAIN + "\"}"));
		cleanUp();
		mailer.clear();
	}

	@AfterEach
	void cleanUp() {
		jdbc.sql("delete from one_time_tokens where username like :pattern").param("pattern", "%" + DOMAIN).update();
		jdbc.sql("delete from app_user where email like :pattern").param("pattern", "%" + DOMAIN).update();
	}

	protected int postMagicLink(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}")).andReturn().getResponse().getStatus();
	}

	protected int login(String token) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\"}")).andReturn().getResponse().getStatus();
	}

	/** The token of every recorded mail to {@code email}, in send order. */
	protected List<String> mailedTokens(String email) {
		List<String> tokens = new ArrayList<>();
		for (MailMessage message : mailer.sent()) {
			if (message.to().equals(email)) {
				Matcher matcher = TOKEN.matcher(message.body());
				assertThat(matcher.find()).isTrue();
				tokens.add(matcher.group(1));
			}
		}
		return tokens;
	}

	protected long tokenRows(String email) {
		return jdbc.sql("select count(*) from one_time_tokens where username = :email").param("email", email)
				.query(Long.class).single();
	}

	protected long accountRows(String email) {
		return jdbc.sql("select count(*) from app_user where email = :email").param("email", email)
				.query(Long.class).single();
	}

	/** Runs every task on its own thread, released together by one start gate, and returns the results in order. */
	protected <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			ready.await();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				try {
					results.add(future.get());
				} catch (ExecutionException exception) {
					throw new AssertionError("a concurrent request failed: " + exception.getCause(),
							exception.getCause());
				}
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}
}
