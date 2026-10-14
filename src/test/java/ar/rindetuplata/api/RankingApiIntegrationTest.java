package ar.rindetuplata.api;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYield;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.TermRate;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletTier;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeding support for the T5c endpoint tests: it writes one controlled row per kind through the T4 store writers,
 * so every expected amount in a test can be hand-computed. The rows are captured on 2026-10-14, the day the
 * recorded fixtures use; the fixed clock makes the ranking's default {@code asOf} 2026-10-20.
 */
abstract class RankingApiIntegrationTest extends ApiIntegrationTest {

	static final LocalDate CAPTURE = LocalDate.parse("2026-10-14");
	static final Instant FETCHED = Instant.parse("2026-10-14T19:06:23Z");

	protected long seedBank(String name, String nonClientTna, String clientTna) {
		return seedBank(name, nonClientTna, clientTna, List.of());
	}

	protected long seedBank(String name, String nonClientTna, String clientTna, List<TermRate> terms) {
		BankPlazoFijoRate rate = new BankPlazoFijoRate(ArgentinaDatosPlazoFijoAdapter.SOURCE, name,
				decimal(clientTna), decimal(nonClientTna), terms, CAPTURE, FETCHED,
				Confidence.AGGREGATOR_AUTOMATIC, List.of());
		bankWriter.write(List.of(rate), CAPTURE);
		return reader.instrument(InstrumentKind.PLAZO_FIJO_BANK, ArgentinaDatosPlazoFijoAdapter.SOURCE.id(), name)
				.orElseThrow().id();
	}

	protected long seedWallet(String walletId, String name, List<WalletTier> tiers) {
		WalletRate rate = new WalletRate(UalaWalletAdapter.SOURCE, walletId, name, tiers, List.of(), CAPTURE, null,
				FETCHED, Confidence.SCRAPED_ENTITY_WEB, List.of());
		walletWriter.write(List.of(rate), CAPTURE);
		return reader.instrument(InstrumentKind.WALLET, UalaWalletAdapter.SOURCE.id(), walletId).orElseThrow().id();
	}

	protected long seedCrypto(String platform, String currency, String apy) {
		CryptoYield yield = new CryptoYield(ArgentinaDatosCryptoAdapter.SOURCE, platform, currency, decimal(apy),
				CAPTURE, FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of());
		cryptoWriter.write(List.of(yield), CAPTURE);
		return reader.instrument(InstrumentKind.CRYPTO_YIELD, ArgentinaDatosCryptoAdapter.SOURCE.id(),
				platform + "|" + currency).orElseThrow().id();
	}

	protected void seedRem(String value, LocalDate observationDate) {
		MacroObservation observation = new MacroObservation(BcraMonetariasClient.SOURCE,
				MacroVariable.EXPECTED_INFLATION_12M, observationDate, decimal(value), FETCHED,
				Confidence.OFFICIAL_AUTOMATIC, List.of());
		macroWriter.write(List.of(observation), observationDate);
	}

	protected static BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
