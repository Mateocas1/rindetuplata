package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.account.MailMessage;
import ar.rindetuplata.account.RecordingMailer;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T11b rule (S4, S13): the 20-active-rule cap holds under concurrency. 25 simultaneous creates by one logged-in
 * user over the HTTP stack leave exactly 20 active rows; the other five answer the existing 400 ProblemDetail.
 * The test is not transactional on purpose: every request commits on its own connection, like production.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=https://app.rindetuplata.ar/ingresar",
		"rindetuplata.web.allowed-origins=http://localhost:4321"
})
@AutoConfigureMockMvc
@Import(AccountTestConfiguration.class)
class AlertRuleCapConcurrencyTest {

	private static final String EMAIL = "concurrent-cap@example.com";

	private static final Pattern TOKEN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RecordingMailer mailer;

	@Autowired
	JdbcClient jdbc;

	@Test
	void twentyFiveConcurrentCreatesLeaveExactlyTwentyActiveRules() throws Exception {
		Cookie session = login();
		int threads = 25;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Future<MvcResult>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return mockMvc.perform(post("/api/v1/alerts").with(csrf())
							.cookie(session)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"thresholdPercent\":25}"))
						.andReturn();
				}));
			}
			start.countDown();
			int created = 0;
			int refused = 0;
			for (Future<MvcResult> future : results) {
				MvcResult result = future.get(60, TimeUnit.SECONDS);
				int status = result.getResponse().getStatus();
				if (status == 201) {
					created++;
				}
				else {
					refused++;
					assertThat(status).isEqualTo(400);
					assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
					assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.detail"))
						.contains("20");
				}
			}
			assertThat(created).as("created answers").isEqualTo(20);
			assertThat(refused).as("refused answers").isEqualTo(5);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.sql("""
				select count(*) from alert_rule
				where active and user_id = (select id from app_user where email = :email)
				""")
			.param("email", EMAIL)
			.query(Long.class)
			.single()).as("active rules in the table").isEqualTo(20L);
	}

	private Cookie login() throws Exception {
		mailer.clear();
		mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\"}"))
			.andReturn();
		MailMessage message = mailer.last();
		Matcher matcher = TOKEN.matcher(message.body());
		assertThat(matcher.find()).isTrue();
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + matcher.group(1) + "\"}"))
			.andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		for (Cookie cookie : result.getResponse().getCookies()) {
			if ("SESSION".equals(cookie.getName())) {
				return cookie;
			}
		}
		throw new AssertionError("the login response set no session cookie");
	}

}
