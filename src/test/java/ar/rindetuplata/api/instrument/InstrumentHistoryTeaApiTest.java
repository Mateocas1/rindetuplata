package ar.rindetuplata.api.instrument;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.api.ApiTestConfiguration;
import ar.rindetuplata.store.InstrumentKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * T1c (S1): every point of the history is also served on one comparable, effective-annual basis. A rate point
 * carries {@code tea} (TNA compounded the way its instrument compounds; an effective basis passes through), a fund
 * quote carries the TEA of {@code FundYield}, and a monthly inflation point carries {@code annualized} = (1 + m)^12 - 1.
 * Expected values are computed independently with Python {@code Decimal} at 50 digits, rounded HALF_EVEN to scale 6.
 * The seed is hand-made rows, so each kind's rate is a round number.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *"
})
@AutoConfigureMockMvc
@Import(ApiTestConfiguration.class)
@Transactional
class InstrumentHistoryTeaApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void aPlazoFijoTnaPointIsCompoundedEveryThirtyDays() throws Exception {
		long id = instrument(InstrumentKind.PLAZO_FIJO_BANK, "pf");
		snapshot(id, "TNA", "0.30", "0.28");

		// (1 + 0.30 * 30 / 365)^(365 / 30) - 1
		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points[0].tea").value(0.344956))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"tea\":0.344956")));
	}

	@Test
	void aWalletTnaPointIsCompoundedDaily() throws Exception {
		long id = instrument(InstrumentKind.WALLET, "wallet");
		snapshot(id, "TNA", "0.30", null);

		// (1 + 0.30 / 365)^365 - 1
		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points[0].tea").value(0.349692));
	}

	@Test
	void anAlreadyEffectiveBasisPassesThroughAndAMissingRateHasNoTea() throws Exception {
		long apy = instrument(InstrumentKind.CRYPTO_YIELD, "apy");
		snapshot(apy, "APY", "0.05", null);
		long tea = instrument(InstrumentKind.PLAZO_FIJO_OFFICIAL, "tea");
		snapshot(tea, "TEA", "0.41", null);
		long none = instrument(InstrumentKind.WALLET, "norate");
		snapshot(none, "TNA", null, null);

		mockMvc.perform(get("/api/v1/instruments/{id}/history", apy))
				.andExpect(jsonPath("$.points[0].basis").value("APY"))
				.andExpect(jsonPath("$.points[0].tea").value(0.05));
		mockMvc.perform(get("/api/v1/instruments/{id}/history", tea))
				.andExpect(jsonPath("$.points[0].basis").value("TEA"))
				.andExpect(jsonPath("$.points[0].tea").value(0.41));
		mockMvc.perform(get("/api/v1/instruments/{id}/history", none))
				.andExpect(jsonPath("$.points[0].rate").value(nullValue()))
				.andExpect(jsonPath("$.points[0].tea").value(nullValue()));
	}

	@Test
	void aFundQuoteCarriesTheTeaOfItsYieldAndAFallingVcpHasNone() throws Exception {
		long id = instrument(InstrumentKind.FUND_CLASS, "fund");
		quote(id, "2026-10-06", "100.000");
		quote(id, "2026-10-07", "100.500");
		quote(id, "2026-10-08", "100.000");

		// (100.500 / 100.000)^365 - 1; the TNA-equivalent is 0.005 * 365 = 1.825.
		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quotes", hasSize(3)))
				.andExpect(jsonPath("$.quotes[0].tea").value(nullValue()))
				.andExpect(jsonPath("$.quotes[1].tnaEquivalent").value(1.825))
				.andExpect(jsonPath("$.quotes[1].tea").value(5.174653))
				.andExpect(jsonPath("$.quotes[2].tnaEquivalent").value(nullValue()))
				.andExpect(jsonPath("$.quotes[2].tea").value(nullValue()));
	}

	@Test
	void everyMonthlyInflationPointCarriesItsAnnualizedValue() throws Exception {
		long id = instrument(InstrumentKind.PLAZO_FIJO_BANK, "pf");
		jdbc.sql("""
				insert into macro_observation (variable, observation_date, source_id, value, fetched_at, confidence, flags)
				values ('MONTHLY_INFLATION', '2026-09-30', 'bcra-estadisticas-monetarias', 0.02, now(),
				        'OFFICIAL_AUTOMATIC', '[]'::jsonb)
				""").update();

		// 1.02^12 - 1, not 0.02 * 12
		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(jsonPath("$.monthlyInflation", hasSize(1)))
				.andExpect(jsonPath("$.monthlyInflation[0].value").value(0.02))
				.andExpect(jsonPath("$.monthlyInflation[0].annualized").value(0.268242));
	}

	@Test
	void theExistingHistoryFieldsAreUnchanged() throws Exception {
		long id = instrument(InstrumentKind.PLAZO_FIJO_BANK, "pf");
		snapshot(id, "TNA", "0.30", "0.28");
		jdbc.sql("""
				insert into macro_observation (variable, observation_date, source_id, value, fetched_at, confidence, flags)
				values ('MONTHLY_INFLATION', '2026-09-30', 'bcra-estadisticas-monetarias', 0.02, now(),
				        'OFFICIAL_AUTOMATIC', '[]'::jsonb)
				""").update();

		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(jsonPath("$.id").value(String.valueOf(id)))
				.andExpect(jsonPath("$.kind").value("PLAZO_FIJO_BANK"))
				.andExpect(jsonPath("$.sourceId").value("test-source"))
				.andExpect(jsonPath("$.from").value("2026-07-23"))
				.andExpect(jsonPath("$.to").value("2026-10-20"))
				.andExpect(jsonPath("$.quotes", hasSize(0)))
				.andExpect(jsonPath("$.points[0].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$.points[0].dataDate").value("2026-10-14"))
				.andExpect(jsonPath("$.points[0].basis").value("TNA"))
				.andExpect(jsonPath("$.points[0].rate").value(0.30))
				.andExpect(jsonPath("$.points[0].clientRate").value(0.28))
				.andExpect(jsonPath("$.points[0].invalid").value(false))
				.andExpect(jsonPath("$.points[0].flags", hasSize(0)))
				.andExpect(jsonPath("$.monthlyInflation[0].index").value("INDEC_CPI_MONTHLY"))
				.andExpect(jsonPath("$.monthlyInflation[0].observationDate").value("2026-09-30"))
				.andExpect(jsonPath("$.monthlyInflation[0].confidence").value("OFFICIAL_AUTOMATIC"))
				.andExpect(jsonPath("$.monthlyInflation[0].sourceId").value("bcra-estadisticas-monetarias"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"rate\":0.300000")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"value\":0.020000")));
	}

	private long instrument(InstrumentKind kind, String naturalKey) {
		return jdbc.sql("""
				insert into instrument (kind, source_id, natural_key, display_name, first_seen, last_seen)
				values (:kind, 'test-source', :key, :key, '2026-10-06', '2026-10-14')
				returning id
				""").param("kind", kind.name()).param("key", naturalKey + "-" + kind.name()).query(Long.class).single();
	}

	private void snapshot(long instrumentId, String basis, String rate, String clientRate) {
		jdbc.sql("""
				insert into rate_snapshot (instrument_id, capture_date, data_date, basis, rate, client_rate, confidence,
				                           fetched_at, flags)
				values (:id, '2026-10-14', '2026-10-14', :basis, cast(:rate as numeric), cast(:clientRate as numeric),
				        'AGGREGATOR_AUTOMATIC', now(), '[]'::jsonb)
				""").param("id", instrumentId).param("basis", basis).param("rate", rate)
				.param("clientRate", clientRate).update();
	}

	private void quote(long instrumentId, String date, String vcp) {
		jdbc.sql("""
				insert into fund_quote (instrument_id, quote_date, vcp, fetched_at, confidence, flags)
				values (:id, cast(:date as date), cast(:vcp as numeric), now(), 'AGGREGATOR_AUTOMATIC', '[]'::jsonb)
				""").param("id", instrumentId).param("date", date).param("vcp", vcp).update();
	}
}
