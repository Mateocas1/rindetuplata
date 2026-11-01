package ar.rindetuplata.ingest.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.ingest.health.IngestionHealthTestConfiguration.MovableClock;
import ar.rindetuplata.store.RunMode;
import ar.rindetuplata.store.RunStatus;
import ar.rindetuplata.store.SourceRunWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * L33b (S4): the {@code ingestion} health indicator over the newest LIVE {@code source_run} that succeeded. Stale data
 * is the custom status {@code STALE}: HTTP 200 on {@code /actuator/health} (Docker's healthcheck and
 * {@code depends_on: service_healthy} keep passing, Caddy keeps serving) and HTTP 503 on the health group
 * {@code /actuator/health/ingestion} that an external monitor watches. The other indicators are switched off because
 * the test context has no broker or mail server, which would make the whole of {@code /actuator/health} DOWN.
 * Everything is requested without a login: both paths are public, like health always was.
 */
@SpringBootTest(properties = { "rindetuplata.ingest.enabled=false", "management.health.rabbit.enabled=false",
		"management.health.mail.enabled=false" })
@AutoConfigureMockMvc
@Import(IngestionHealthTestConfiguration.class)
@Transactional
class IngestionHealthApiTest {

	static final LocalDate DAY = LocalDate.parse("2026-10-20");

	/** The 19:30 run of {@link #DAY}, a minute in: 22:31Z. */
	static final Instant RECORDED = Instant.parse("2026-10-20T22:31:00Z");

	/** 2026-10-22 07:30 in Buenos Aires: 36 hours after the expected 19:30 run of {@link #DAY}. */
	static final Instant DEADLINE = Instant.parse("2026-10-22T10:30:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MovableClock clock;

	@Autowired
	SourceRunWriter sourceRunWriter;

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	HealthEndpointGroups healthGroups;

	@Test
	void anIngestedDayThatIsStillInsideTheWindowIsUpOnBothPathsWithItsDateAndAge() throws Exception {
		sourceRunWriter.record(DAY, "bcra-official", RunMode.LIVE, RunStatus.OK, 14, null, RECORDED);
		clock.set(Instant.parse("2026-10-21T12:00:00Z"));

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
		mockMvc.perform(get("/actuator/health/ingestion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.ingestionFreshness.status").value("UP"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessDate").value("2026-10-20"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessAt").value("2026-10-20T22:31:00Z"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.ageSeconds").value(48540))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.staleAfter").value("PT36H"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.staleAt").value("2026-10-22T10:30:00Z"));
	}

	@Test
	void staleDataKeepsTheMainHealthAt200ButFailsTheIngestionGroupWith503() throws Exception {
		sourceRunWriter.record(DAY, "bcra-official", RunMode.LIVE, RunStatus.OK, 14, null, RECORDED);
		clock.set(DEADLINE.plusSeconds(1));

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("STALE"))
				.andExpect(jsonPath("$.components").doesNotExist());
		mockMvc.perform(get("/actuator/health/ingestion"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("STALE"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessDate").value("2026-10-20"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.ageSeconds").value(129541))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.staleAt").value("2026-10-22T10:30:00Z"));
	}

	@Test
	void theDeadlineSecondIsStillUp() throws Exception {
		sourceRunWriter.record(DAY, "bcra-official", RunMode.LIVE, RunStatus.OK, 14, null, RECORDED);
		clock.set(DEADLINE);

		mockMvc.perform(get("/actuator/health/ingestion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void aStoreWithNoSuccessfulLiveRunIsStaleAndSaysSo() throws Exception {
		clock.set(Instant.parse("2026-10-21T12:00:00Z"));

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("STALE"));
		mockMvc.perform(get("/actuator/health/ingestion"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("STALE"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.reason").value("no successful LIVE ingestion is recorded"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessDate").doesNotExist())
				.andExpect(jsonPath("$.components.ingestionFreshness.details.ageSeconds").doesNotExist());
		assertThat(ageGauge()).isNaN();
	}

	@Test
	void onlyOkAndDegradedLiveRunsCountAsASuccess() throws Exception {
		LocalDate older = DAY.minusDays(3);
		sourceRunWriter.record(older, "bcra-official", RunMode.LIVE, RunStatus.DEGRADED, 5, "another source failed",
				Instant.parse("2026-10-17T22:31:00Z"));
		// Newer days that must not move "the newest success": a failure, an archive miss, a replay and a rowless
		// duplicate of the older day's source.
		sourceRunWriter.record(DAY.minusDays(2), "bcra-official", RunMode.LIVE, RunStatus.FAILED, 0, "timeout",
				Instant.parse("2026-10-18T22:31:00Z"));
		sourceRunWriter.record(DAY.minusDays(1), "bcra-official", RunMode.REPLAY, RunStatus.MISSING_IN_ARCHIVE, 0, null,
				Instant.parse("2026-10-19T22:31:00Z"));
		sourceRunWriter.record(DAY, "bcra-official", RunMode.REPLAY, RunStatus.OK, 14, null, RECORDED);
		clock.set(Instant.parse("2026-10-18T12:00:00Z"));

		mockMvc.perform(get("/actuator/health/ingestion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessDate").value("2026-10-17"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessAt").value("2026-10-17T22:31:00Z"));
	}

	@Test
	void theNewestCaptureDateWinsAndAnyOfItsSourcesCounts() throws Exception {
		sourceRunWriter.record(DAY, "bcra-official", RunMode.LIVE, RunStatus.FAILED, 0, "timeout", RECORDED);
		sourceRunWriter.record(DAY, "dolar-api", RunMode.LIVE, RunStatus.OK, 3, null, RECORDED.plusSeconds(30));
		sourceRunWriter.record(DAY.minusDays(1), "bcra-official", RunMode.LIVE, RunStatus.OK, 14, null,
				Instant.parse("2026-10-19T22:31:00Z"));
		clock.set(Instant.parse("2026-10-21T12:00:00Z"));

		mockMvc.perform(get("/actuator/health/ingestion"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessDate").value("2026-10-20"))
				.andExpect(jsonPath("$.components.ingestionFreshness.details.lastSuccessAt").value("2026-10-20T22:31:30Z"));
	}

	@Test
	void theGaugeReportsTheAgeOfTheNewestSuccessInSecondsAndFollowsTheClock() {
		sourceRunWriter.record(DAY, "bcra-official", RunMode.LIVE, RunStatus.OK, 14, null, RECORDED);

		clock.set(RECORDED.plusSeconds(90));
		assertThat(ageGauge()).isEqualTo(90.0);

		clock.set(RECORDED.plusSeconds(3600));
		assertThat(ageGauge()).isEqualTo(3600.0);
		assertThat(meterRegistry.get(IngestionHealthConfiguration.AGE_GAUGE).gauge().getId().getBaseUnit())
				.isEqualTo("seconds");
	}

	@Test
	void staleSitsBetweenDownAndUpInTheStatusOrderAndMapsToTheConfiguredCodes() {
		var aggregator = healthGroups.getPrimary().getStatusAggregator();
		Status stale = new Status("STALE");

		assertThat(aggregator.getAggregateStatus(Status.DOWN, stale, Status.UP)).isEqualTo(Status.DOWN);
		assertThat(aggregator.getAggregateStatus(Status.OUT_OF_SERVICE, stale, Status.UP)).isEqualTo(Status.OUT_OF_SERVICE);
		assertThat(aggregator.getAggregateStatus(stale, Status.UP)).isEqualTo(stale);
		assertThat(aggregator.getAggregateStatus(Status.UP, Status.UP)).isEqualTo(Status.UP);

		assertThat(healthGroups.getPrimary().getHttpCodeStatusMapper().getStatusCode(stale)).isEqualTo(200);
		assertThat(healthGroups.getPrimary().getHttpCodeStatusMapper().getStatusCode(Status.UP)).isEqualTo(200);
		// Naming one mapping replaces Boot's defaults: a DOWN database must still fail Docker's healthcheck.
		assertThat(healthGroups.getPrimary().getHttpCodeStatusMapper().getStatusCode(Status.DOWN)).isEqualTo(503);
		assertThat(healthGroups.getPrimary().getHttpCodeStatusMapper().getStatusCode(Status.OUT_OF_SERVICE))
				.isEqualTo(503);
		var group = healthGroups.get("ingestion");
		assertThat(group.getHttpCodeStatusMapper().getStatusCode(stale)).isEqualTo(503);
		assertThat(group.getHttpCodeStatusMapper().getStatusCode(Status.DOWN)).isEqualTo(503);
		assertThat(group.getHttpCodeStatusMapper().getStatusCode(Status.OUT_OF_SERVICE)).isEqualTo(503);
		assertThat(group.getHttpCodeStatusMapper().getStatusCode(Status.UP)).isEqualTo(200);
	}

	private double ageGauge() {
		return meterRegistry.get(IngestionHealthConfiguration.AGE_GAUGE).gauge().value();
	}
}
