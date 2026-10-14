package ar.rindetuplata.api;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.ValidationFlag;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The recorded fixtures the {@code api} tests seed through the T4 store writers, and a few hand-made fund quotes. */
final class ApiFixtures {

	/** The capture instant of the recorded 2026-10-14 archive, so the flags match production. */
	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");
	static final LocalDate CAPTURE = LocalDate.parse("2026-10-14");

	/** A class whose quotes rose and then fell, to pin the null TNA-equivalent. */
	static final String FALLEN_FUND = "Fallen Money Market - Clase A";

	private ApiFixtures() {
	}

	static List<OfficialPlazoFijoRate> official() {
		return BcraPlazoFijoAdapter.parse(Fixtures.bytes("bcra/transparencia-plazos-fijos.json"), FETCHED);
	}

	static List<BankPlazoFijoRate> banks() {
		return ArgentinaDatosPlazoFijoAdapter.parse(Fixtures.bytes("argentinadatos/plazo-fijo.json"), FETCHED);
	}

	static List<FundQuote> fundQuotes(String fixture) {
		return ArgentinaDatosFundAdapter.parse(Fixtures.bytes(fixture), FETCHED);
	}

	static List<CryptoYield> crypto() {
		return ArgentinaDatosCryptoAdapter.parse(Fixtures.bytes("argentinadatos/rendimientos.json"), FETCHED);
	}

	static WalletRate uala() {
		return UalaWalletAdapter.parseExtract(Fixtures.bytes("uala/cuenta-remunerada-extract.json"), FETCHED);
	}

	static WalletRate curatedWallet() {
		return CuratedWalletRateAdapter.parse(seed(), FETCHED).get(0);
	}

	static List<MacroObservation> monthlyInflation() {
		return BcraMonetariasClient.parse(Fixtures.bytes("bcra/monetarias-27.json"), MacroVariable.MONTHLY_INFLATION,
				FETCHED);
	}

	/**
	 * 100.000 on 10-06, 100.500 on 10-07 (daily 0.5%, TNA 1.825), back to 100.000 on 10-08 and an INVALID zero quote
	 * on 10-09, which is stored for audit but must stay out of the series.
	 */
	static List<FundQuote> fallenFundQuotes() {
		return List.of(fundQuote("2026-10-06", "100.000", List.of()), fundQuote("2026-10-07", "100.500", List.of()),
				fundQuote("2026-10-08", "100.000", List.of()), fundQuote("2026-10-09", "0",
						List.of(new ValidationFlag(FlagCode.INVALID_QUOTE, Severity.INVALID,
								"vcp is missing or not positive"))));
	}

	private static FundQuote fundQuote(String date, String vcp, List<ValidationFlag> flags) {
		return new FundQuote(ArgentinaDatosFundAdapter.SOURCE, FALLEN_FUND, FALLEN_FUND, null, "corto", Segment.SAVER,
				LocalDate.parse(date), new BigDecimal(vcp), null, null, FETCHED, Confidence.AGGREGATOR_AUTOMATIC,
				flags);
	}

	private static byte[] seed() {
		try (InputStream in = ApiFixtures.class.getResourceAsStream("/curated-wallet-rates.json")) {
			if (in == null) {
				throw new IllegalStateException("Missing /curated-wallet-rates.json");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
