package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.dollar.ArgentinaDatosDollarAdapter;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.store.DollarQuoteWriter;
import ar.rindetuplata.store.StoreTestConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * T4a (L33 a): the MEP reader takes the latest quote at or before {@code asOf} whatever the source, and on the same
 * date prefers DolarApi (the live capture) over the ArgentinaDatos history. Ids sort the other way
 * ({@code argentinadatos-...} before {@code dolarapi-...}), so the preference must be explicit, not an accident of
 * the tie break.
 */
@Import(StoreTestConfiguration.class)
@SpringBootTest
@Transactional
class MepSourcePreferenceTest {

	private static final LocalDate DAY = LocalDate.parse("2026-10-01");
	private static final Instant AT = Instant.parse("2026-11-01T12:00:00Z");

	@Autowired
	RankingReader reader;

	@Autowired
	DollarQuoteWriter writer;

	private void store(SourceRef source, LocalDate date, String buy, String sell) {
		writer.write(List.of(new DollarQuote(source, new BigDecimal(buy), new BigDecimal(sell), null, AT,
				Confidence.AGGREGATOR_AUTOMATIC, List.of())), date);
	}

	@Test
	void onTheSameDateDolarApiWinsOverTheArgentinaDatosHistory() {
		store(ArgentinaDatosDollarAdapter.SOURCE, DAY, "1537.2", "1548.7");
		store(DolarApiAdapter.SOURCE, DAY, "1534.2", "1539.4");

		MepQuote quote = reader.latestDollarMep(DAY).orElseThrow();

		assertThat(quote.source().id()).isEqualTo(DolarApiAdapter.SOURCE.id());
		assertThat(quote.buy()).isEqualByComparingTo("1534.2");
		assertThat(quote.sell()).isEqualByComparingTo("1539.4");
		assertThat(quote.quoteDate()).isEqualTo(DAY);
	}

	@Test
	void aNewerDateWinsWhateverTheSource() {
		store(DolarApiAdapter.SOURCE, DAY, "1534.2", "1539.4");
		store(ArgentinaDatosDollarAdapter.SOURCE, DAY.plusDays(1), "1540", "1550");

		MepQuote quote = reader.latestDollarMep(DAY.plusDays(1)).orElseThrow();

		assertThat(quote.source().id()).isEqualTo(ArgentinaDatosDollarAdapter.SOURCE.id());
		assertThat(quote.quoteDate()).isEqualTo(DAY.plusDays(1));
		assertThat(quote.sell()).isEqualByComparingTo("1550");
	}

	@Test
	void theHistoryAloneAnswersAndIsNamedWithItsOwnSource() {
		store(ArgentinaDatosDollarAdapter.SOURCE, DAY, "1537.2", "1548.7");

		MepQuote quote = reader.latestDollarMep(DAY.plusDays(3)).orElseThrow();

		assertThat(quote.source().id()).isEqualTo("argentinadatos-dolar-bolsa");
		assertThat(quote.source().displayName()).isEqualTo("ArgentinaDatos");
		assertThat(quote.source().official()).isFalse();
		assertThat(quote.quoteDate()).isEqualTo(DAY);
	}

	@Test
	void aQuoteAfterAsOfIsNeverUsed() {
		store(ArgentinaDatosDollarAdapter.SOURCE, DAY.plusDays(1), "1540", "1550");

		assertThat(reader.latestDollarMep(DAY)).isEmpty();
	}

	@Test
	void aNonPositiveQuoteOfThePreferredSourceFallsBackToTheOtherOneOnTheSameDate() {
		store(DolarApiAdapter.SOURCE, DAY, "0", "1539.4");
		store(ArgentinaDatosDollarAdapter.SOURCE, DAY, "1537.2", "1548.7");

		MepQuote quote = reader.latestDollarMep(DAY).orElseThrow();

		assertThat(quote.source().id()).isEqualTo(ArgentinaDatosDollarAdapter.SOURCE.id());
	}
}
