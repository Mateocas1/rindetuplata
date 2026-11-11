package ar.rindetuplata.ingest;

import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.source.dollar.ArgentinaDatosDollarAdapter;
import ar.rindetuplata.source.dollar.DatedDollarQuote;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.OfficialPlazoFijoRate;
import ar.rindetuplata.source.wallet.CuratedWalletRateAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletRate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/**
 * The fixed records the LIVE port stubs answer with: the recorded fixtures through the same static parsers the
 * archive replay uses, never live HTTP.
 */
final class IngestFixtures {

	/** The capture instant of the recorded 2026-10-14 archive. */
	static final Instant FETCHED_AT = Instant.parse("2026-10-14T19:06:23Z");

	private IngestFixtures() {
	}

	static List<OfficialPlazoFijoRate> officialRates() {
		return BcraPlazoFijoAdapter.parse(Fixtures.bytes("bcra/transparencia-plazos-fijos.json"), FETCHED_AT);
	}

	static List<BankPlazoFijoRate> bankRates() {
		return ArgentinaDatosPlazoFijoAdapter.parse(Fixtures.bytes("argentinadatos/plazo-fijo.json"), FETCHED_AT);
	}

	static List<FundQuote> fundLatest() {
		return ArgentinaDatosFundAdapter.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-ultimo.json"),
				FETCHED_AT);
	}

	static List<FundQuote> fundPrevious() {
		return ArgentinaDatosFundAdapter.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-penultimo.json"),
				FETCHED_AT);
	}

	/** The recorded 2026-10-01 classes of the dated FCI endpoint, re-dated to the requested day. */
	static List<FundQuote> fundDay(LocalDate day) {
		return ArgentinaDatosFundAdapter
			.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-2026-10-01-excerpt.json"), FETCHED_AT)
			.stream()
			.map(quote -> new FundQuote(quote.source(), quote.name(), quote.fund(), quote.shareClass(),
					quote.horizon(), quote.segment(), day, quote.vcp(), quote.ccp(), quote.patrimonio(),
					quote.fetchedAt(), quote.confidence(), quote.flags()))
			.toList();
	}

	/** The recorded excerpt of the ArgentinaDatos MEP series, 2026-09-28 to 2026-10-06. */
	static List<DatedDollarQuote> dollarSeries() {
		return ArgentinaDatosDollarAdapter.parse(
				Fixtures.bytes("argentinadatos/dolares-bolsa-serie-2026-09-28-2026-10-06-excerpt.json"), FETCHED_AT);
	}

	/** The recorded macro observations of the variable that fall inside the inclusive range. */
	static List<MacroObservation> macroRange(MacroVariable variable, LocalDate from, LocalDate to) {
		return macro(variable).stream()
			.filter(observation -> !observation.date().isBefore(from) && !observation.date().isAfter(to))
			.toList();
	}

	static List<CryptoYield> cryptoYields() {
		return ArgentinaDatosCryptoAdapter.parse(Fixtures.bytes("argentinadatos/rendimientos.json"), FETCHED_AT);
	}

	static List<MacroObservation> macro(MacroVariable variable) {
		String fixture = switch (variable) {
			case MONTHLY_INFLATION -> "bcra/monetarias-27.json";
			case EXPECTED_INFLATION_12M -> "bcra/monetarias-29.json";
			case PSP_BENCHMARK_TNA -> "bcra/monetarias-1196.json";
		};
		return BcraMonetariasClient.parse(Fixtures.bytes(fixture), variable, FETCHED_AT);
	}

	static DollarQuote dollar() {
		return DolarApiAdapter.parse(Fixtures.bytes("dolarapi/dolar-bolsa.json"), FETCHED_AT);
	}

	static WalletRate uala() {
		return UalaWalletAdapter.parseExtract(Fixtures.bytes("uala/cuenta-remunerada-extract.json"), FETCHED_AT);
	}

	static List<WalletRate> curated() {
		try {
			return CuratedWalletRateAdapter.parse(
					new ClassPathResource("curated-wallet-rates.json").getContentAsByteArray(), FETCHED_AT);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
