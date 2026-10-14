package ar.rindetuplata.ranking;

import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.macro.BcraMonetariasClient;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BcraPlazoFijoAdapter;
import ar.rindetuplata.source.wallet.CuratedWalletRateAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import java.util.Map;

/**
 * Reads a stored {@code source_id} back into the T3 {@code SourceRef} the API shows (S11): display name and
 * official flag. The declaration lives with the adapter, so it is never duplicated; an id with no adapter here
 * falls back to itself, never invented as official.
 */
final class SourceRegistry {

	/** The classpath wallet-to-fund mapping: its id matches {@code FciBackedWalletCatalog.SOURCE}. */
	static final SourceView FCI_BACKED_WALLETS = new SourceView("fci-backed-wallets", "FCI-backed wallet mapping",
			false);

	private static final Map<String, SourceView> SOURCES = Map.of(
			BcraPlazoFijoAdapter.SOURCE.id(), SourceView.of(BcraPlazoFijoAdapter.SOURCE),
			ArgentinaDatosPlazoFijoAdapter.SOURCE.id(), SourceView.of(ArgentinaDatosPlazoFijoAdapter.SOURCE),
			ArgentinaDatosFundAdapter.SOURCE.id(), SourceView.of(ArgentinaDatosFundAdapter.SOURCE),
			ArgentinaDatosCryptoAdapter.SOURCE.id(), SourceView.of(ArgentinaDatosCryptoAdapter.SOURCE),
			UalaWalletAdapter.SOURCE.id(), SourceView.of(UalaWalletAdapter.SOURCE),
			CuratedWalletRateAdapter.SOURCE.id(), SourceView.of(CuratedWalletRateAdapter.SOURCE),
			BcraMonetariasClient.SOURCE.id(), SourceView.of(BcraMonetariasClient.SOURCE));

	private SourceRegistry() {
	}

	static SourceView of(String sourceId) {
		return SOURCES.getOrDefault(sourceId, new SourceView(sourceId, sourceId, false));
	}
}
