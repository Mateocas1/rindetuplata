package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * T4d unit rules of the rebuild hook (S16, S14): after a COMPLETED {@code dailyIngestionJob} exactly one POST goes
 * to the deploy hook; a FAILED job and an unset or blank URL never call HTTP at all; a hook that answers an error
 * or times out is logged and the job status is untouched. The 10 s connect and read timeout comes from
 * {@code rindetuplata.ingest.rebuild-hook-timeout}.
 */
class RebuildHookListenerTest {

	private static final String URL = "http://rebuild-hook.test/deploy";

	private final ApplicationContextRunner properties = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfiguration.class);

	private MockRestServiceServer server;

	private RestClient client;

	private RebuildHookListener listener;

	@BeforeEach
	void bindMockServer() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = builder.build();
		listener = new RebuildHookListener(client, URL);
	}

	/** Rule 1: one POST, method POST, to the configured URL, once per COMPLETED job. */
	@Test
	void aCompletedJobPostsOnceToTheHook() {
		server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());

		listener.afterJob(execution(BatchStatus.COMPLETED));

		server.verify();
	}

	/** Rule 2: no URL, an empty URL or a blank URL means no HTTP call at all. */
	@Test
	void anUnsetOrBlankHookUrlNeverCallsHttp() {
		server.expect(never(), requestTo(URL));
		JobExecution completed = execution(BatchStatus.COMPLETED);

		assertThatCode(() -> {
			new RebuildHookListener(client, null).afterJob(completed);
			new RebuildHookListener(client, "").afterJob(completed);
			new RebuildHookListener(client, "   ").afterJob(completed);
		}).doesNotThrowAnyException();

		server.verify();
	}

	/** Rule 3: a FAILED job is not a reason to rebuild; an already-completed no-op never runs the job at all. */
	@Test
	void aFailedJobNeverCallsTheHook() {
		server.expect(never(), requestTo(URL));
		JobExecution failed = execution(BatchStatus.FAILED);

		assertThatCode(() -> listener.afterJob(failed)).doesNotThrowAnyException();

		server.verify();
	}

	/** Rule 4: a 5xx answer is logged, never rethrown, and the job stays COMPLETED. */
	@Test
	void aHookErrorIsLoggedAndTheJobStaysCompleted() {
		server.expect(once(), requestTo(URL)).andRespond(withServerError());
		JobExecution completed = execution(BatchStatus.COMPLETED);

		assertThatCode(() -> listener.afterJob(completed)).doesNotThrowAnyException();

		assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		server.verify();
	}

	/** Rule 4: the same for a hook that never answers (a read timeout surfaces as an I/O failure). */
	@Test
	void aHookTimeoutIsLoggedAndTheJobStaysCompleted() {
		server.expect(once(), requestTo(URL)).andRespond(request -> {
			throw new SocketTimeoutException("read timed out");
		});
		JobExecution completed = execution(BatchStatus.COMPLETED);

		assertThatCode(() -> listener.afterJob(completed)).doesNotThrowAnyException();

		assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		server.verify();
	}

	/** Rule 5: 10 s connect and read by default, following {@code rindetuplata.ingest.rebuild-hook-timeout}. */
	@Test
	void theHookTimeoutIsTenSecondsByDefaultAndConfigurable() {
		properties.run(context -> assertHookProperties(context, Duration.ofSeconds(10)));
		properties.withPropertyValues("rindetuplata.ingest.rebuild-hook-timeout=2s")
			.run(context -> assertHookProperties(context, Duration.ofSeconds(2)));
	}

	private static void assertHookProperties(ApplicationContext context, Duration expected) {
		IngestionProperties bound = context.getBean(IngestionProperties.class);
		assertThat(bound.rebuildHookUrl()).as("unset by default: the hook is off").isBlank();
		assertThat(bound.rebuildHookTimeout()).isEqualTo(expected);

		RebuildHookRequestFactory factory = new RebuildHookRequestFactory(bound.rebuildHookTimeout());
		assertThat(factory.connectTimeout()).isEqualTo(expected);
		assertThat(factory.readTimeout()).isEqualTo(expected);
	}

	private static JobExecution execution(BatchStatus status) {
		JobExecution execution = new JobExecution(1L, new JobInstance(1L, IngestionService.JOB_NAME),
				new JobParameters());
		execution.setStatus(status);
		return execution;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(IngestionProperties.class)
	static class PropertiesConfiguration {

	}

}
