package ar.rindetuplata.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T2c rules on the wire (S2, L5): {@code GET /api/v1/ranking} groups fund classes into one row per fund by default
 * and reports {@code foldedClasses}; {@code groupFunds=false} lists every class; the exclusions keep their class.
 */
class RankingFundGroupingApiTest extends RankingApiIntegrationTest {

	@Test
	void classesOfOneFundCollapseByDefaultAndReportTheFoldedSiblings() throws Exception {
		seedFund("Fondo Uno - Clase A", "1000.5");
		seedFund("Fondo Uno - Clase B", "1000.4");
		seedFund("Fondo Uno - Clase C", "1000.3");

		mockMvc.perform(get("/api/v1/ranking").param("kind", "FUND"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked", hasSize(1)))
				.andExpect(jsonPath("$.ranked[0].name").value("Fondo Uno - Clase A"))
				.andExpect(jsonPath("$.ranked[0].foldedClasses").value(2))
				.andExpect(jsonPath("$.totals.ranked").value(1));
	}

	@Test
	void groupFundsFalseListsEveryClass() throws Exception {
		seedFund("Fondo Uno - Clase A", "1000.5");
		seedFund("Fondo Uno - Clase B", "1000.4");

		mockMvc.perform(get("/api/v1/ranking").param("kind", "FUND").param("groupFunds", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked[*].name").value(contains("Fondo Uno - Clase A", "Fondo Uno - Clase B")))
				.andExpect(jsonPath("$.ranked[*].foldedClasses").value(contains(0, 0)))
				.andExpect(jsonPath("$.totals.ranked").value(2));
	}

	@Test
	void everyOtherKindReportsZeroFoldedClasses() throws Exception {
		seedBank("Banco A", "0.30", null);

		mockMvc.perform(get("/api/v1/ranking").param("kind", "PLAZO_FIJO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked[0].foldedClasses").value(0));
	}

	@Test
	void excludedFundClassesStayOneEntryPerClass() throws Exception {
		seedFund("Delta Dólares - Clase A", "1000.5");
		seedFund("Delta Dólares - Clase B", "1000.5");
		seedFund("Fondo Fiscal - Clase L Ley N° 27.743", "1000.5");

		mockMvc.perform(get("/api/v1/ranking").param("kind", "FUND"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked", hasSize(0)))
				.andExpect(jsonPath("$.excluded[?(@.name=='Delta Dólares - Clase A')].reason")
						.value(contains("FOREIGN_CURRENCY")))
				.andExpect(jsonPath("$.excluded[?(@.name=='Delta Dólares - Clase B')].reason")
						.value(contains("FOREIGN_CURRENCY")))
				.andExpect(jsonPath("$.excluded[?(@.name=='Fondo Fiscal - Clase L Ley N° 27.743')].reason")
						.value(contains("RESTRICTED_CLASS")));
	}

	private void seedFund(String name, String latestVcp) {
		write(name, CAPTURE.minusDays(1), "1000");
		write(name, CAPTURE, latestVcp);
	}

	private void write(String name, LocalDate date, String vcp) {
		int separator = name.indexOf(" - ");
		fundWriter.write(List.of(new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name, name.substring(0, separator),
				name.substring(separator + 3), "corto", Segment.SAVER, date, new BigDecimal(vcp), null, null,
				FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of())), date);
		recordIngestionRun();
	}
}
