package ar.rindetuplata.curation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.ingest.BackfillService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code POST /api/v1/admin/backfill?from=&to=} is admin-only (session, ROLE_ADMIN, CSRF). It answers
 * 202 with the job id, 409 while a run is going and a 400 ProblemDetail naming {@code from} or {@code to} for an
 * inverted range, more than 366 days or a {@code to} after yesterday (the frozen day is 2026-10-20, so yesterday is
 * 2026-10-19). A rejected request starts nothing and stores nothing.
 */
@Import(BackfillAdminStubs.class)
class BackfillAdminApiTest extends CurationIntegrationTest {

	private static final String PATH = "/api/v1/admin/backfill";

	@Autowired
	BackfillService backfill;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void startFromAnIdleService() {
		BackfillAdminStubs.FUND_DAYS.clear();
		BackfillAdminStubs.hold = null;
		awaitIdle();
	}

	@AfterEach
	void releaseAnyHeldRun() {
		CountDownLatch latch = BackfillAdminStubs.hold;
		if (latch != null) {
			latch.countDown();
		}
		BackfillAdminStubs.hold = null;
		awaitIdle();
	}

	private void awaitIdle() {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (backfill.running() && System.nanoTime() < deadline) {
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}
		assertThat(backfill.running()).as("the backfill finished").isFalse();
	}

	private ResultActions backfillRequest(Cookie session, String query) throws Exception {
		MockHttpServletRequestBuilder builder = post(PATH + query).with(csrf());
		return mockMvc.perform(session == null ? builder : builder.cookie(session));
	}

	private long sourceRuns() {
		return jdbc.sql("select count(*) from source_run").query(Long.class).single();
	}

	private int jobInstances() {
		return jobRepository.getJobInstances("historyBackfillJob", 0, 1000).size();
	}

	private void expectNothingStarted(long runsBefore, int instancesBefore) {
		assertThat(backfill.running()).isFalse();
		assertThat(jobInstances()).as("no job instance was created").isEqualTo(instancesBefore);
		assertThat(sourceRuns()).as("no source run was stored").isEqualTo(runsBefore);
		assertThat(BackfillAdminStubs.FUND_DAYS).as("no source was called").isEmpty();
	}

	@Test
	void anAdminStartsABackfillAndGetsTheJobIdWith202() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);

		String body = backfillRequest(admin, "?from=2026-10-01&to=2026-10-03")
				.andExpect(status().isAccepted())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.jobId").isNumber())
				.andExpect(jsonPath("$.from").value("2026-10-01"))
				.andExpect(jsonPath("$.to").value("2026-10-03"))
				.andReturn().getResponse().getContentAsString();
		long jobId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.jobId")).longValue();

		awaitIdle();
		JobExecution execution = jobRepository.getJobExecution(jobId);
		assertThat(execution.getJobInstance().getJobName()).isEqualTo("historyBackfillJob");
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(BackfillAdminStubs.FUND_DAYS).containsExactly(LocalDate.parse("2026-10-01"),
				LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-03"));
		List<String> modes = jdbc.sql("""
				select distinct mode from source_run where capture_date between '2026-10-01' and '2026-10-03'
				""").query(String.class).list();
		assertThat(modes).containsExactly("BACKFILL");
	}

	@Test
	void aSecondRunWhileOneIsRunningIsA409AndTheNextOneAfterItFinishesIsAccepted() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		BackfillAdminStubs.hold = new CountDownLatch(1);
		backfillRequest(admin, "?from=2026-10-01&to=2026-10-02").andExpect(status().isAccepted());
		long runs = sourceRuns();
		int instances = jobInstances();

		expectProblem(backfillRequest(admin, "?from=2026-10-05&to=2026-10-06"), 409, "Conflict")
				.andExpect(jsonPath("$.detail")
						.value("A history backfill is already running; wait for it to finish before starting another"));

		assertThat(jobInstances()).as("the rejected request started nothing").isEqualTo(instances);
		assertThat(sourceRuns()).isEqualTo(runs);

		BackfillAdminStubs.hold.countDown();
		awaitIdle();
		backfillRequest(admin, "?from=2026-10-05&to=2026-10-06").andExpect(status().isAccepted());
		awaitIdle();
	}

	@Test
	void anInvertedRangeIs400NamingFrom() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		long runs = sourceRuns();
		int instances = jobInstances();

		expectProblem(backfillRequest(admin, "?from=2026-10-05&to=2026-10-01"), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value("from"))
				.andExpect(jsonPath("$.detail").value("'from' (2026-10-05) must not be after 'to' (2026-10-01)"));

		expectNothingStarted(runs, instances);
	}

	@Test
	void aToAfterYesterdayIs400NamingTo() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		long runs = sourceRuns();
		int instances = jobInstances();

		for (String to : List.of("2026-10-20", "2026-10-21", "2027-01-01")) {
			expectProblem(backfillRequest(admin, "?from=2026-10-01&to=" + to), 400, "Bad Request")
					.andExpect(jsonPath("$.parameter").value("to"))
					.andExpect(jsonPath("$.detail").value("'to' (" + to
							+ ") must not be after yesterday (2026-10-19): a backfill never reaches into today"));
		}

		expectNothingStarted(runs, instances);
	}

	@Test
	void moreThan366DaysIs400NamingTo() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		long runs = sourceRuns();
		int instances = jobInstances();

		expectProblem(backfillRequest(admin, "?from=2025-10-18&to=2026-10-19"), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value("to"))
				.andExpect(jsonPath("$.detail").value("The range from 2025-10-18 to 2026-10-19 covers 367 days; "
						+ "at most 366 are allowed per run ('from' and 'to' are inclusive)"));

		expectNothingStarted(runs, instances);
	}

	@Test
	void exactly366DaysEndingYesterdayIsAccepted() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);

		backfillRequest(admin, "?from=2025-10-19&to=2026-10-19").andExpect(status().isAccepted())
				.andExpect(jsonPath("$.from").value("2025-10-19"))
				.andExpect(jsonPath("$.to").value("2026-10-19"));
		awaitIdle();

		assertThat(BackfillAdminStubs.FUND_DAYS).hasSize(366);
	}

	@Test
	void aMissingOrMalformedDateIs400NamingTheParameter() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		long runs = sourceRuns();
		int instances = jobInstances();

		expectProblem(backfillRequest(admin, "?to=2026-10-03"), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value("from"))
				.andExpect(jsonPath("$.detail").value("Required parameter 'from' is missing"));
		expectProblem(backfillRequest(admin, "?from=2026-10-01"), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value("to"));
		expectProblem(backfillRequest(admin, "?from=2026-10-01&to=yesterday"), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value("to"))
				.andExpect(jsonPath("$.detail").value(containsString("'to'")));
		expectProblem(backfillRequest(admin, "?from=01/10/2026&to=2026-10-03"), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value("from"));

		expectNothingStarted(runs, instances);
	}

	@Test
	void aPlainUserIsForbiddenAnAnonymousCallerIsUnauthorizedAndNothingStarts() throws Exception {
		Cookie user = loginNewSession(USER_EMAIL);
		long runs = sourceRuns();
		int instances = jobInstances();

		expectProblem(backfillRequest(user, "?from=2026-10-01&to=2026-10-03"), 403, "Forbidden");
		expectProblem(backfillRequest(null, "?from=2026-10-01&to=2026-10-03"), 401, "Unauthorized");

		expectNothingStarted(runs, instances);
	}

	@Test
	void anAdminWithoutTheCsrfTokenIsForbiddenAndNothingStarts() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		long runs = sourceRuns();
		int instances = jobInstances();

		mockMvc.perform(post(PATH + "?from=2026-10-01&to=2026-10-03").cookie(admin))
				.andExpect(status().isForbidden());

		expectNothingStarted(runs, instances);
	}
}
