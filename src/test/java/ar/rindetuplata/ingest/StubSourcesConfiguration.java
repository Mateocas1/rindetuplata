package ar.rindetuplata.ingest;

import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceStatus;
import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.crypto.CryptoYieldSource;
import ar.rindetuplata.source.dollar.DollarSource;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.fund.FundSource;
import ar.rindetuplata.source.macro.MacroObservation;
import ar.rindetuplata.source.macro.MacroSource;
import ar.rindetuplata.source.macro.MacroVariable;
import ar.rindetuplata.source.plazofijo.PlazoFijoReport;
import ar.rindetuplata.source.plazofijo.PlazoFijoSource;
import ar.rindetuplata.source.wallet.WalletRateSource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Replaces every external port with a stub that answers the recorded fixtures, so a LIVE test never touches the
 * network. The real adapter beans are removed from the registry before anything is instantiated (bean definition
 * overriding is order dependent, this is not), and the stubs take their place by type.
 */
@TestConfiguration(proxyBeanMethods = false)
class StubSourcesConfiguration implements BeanDefinitionRegistryPostProcessor {

	/** The real adapter beans this context must not instantiate: they fetch over HTTP. */
	private static final List<String> REAL_SOURCE_BEANS = List.of("plazoFijoSource", "bcraPlazoFijoAdapter",
			"argentinaDatosPlazoFijoAdapter", "macroSource", "bcraMonetariasClient", "argentinaDatosInflationAdapter",
			"argentinaDatosFundAdapter", "argentinaDatosCryptoAdapter", "dolarApiAdapter", "ualaWalletAdapter",
			"curatedWalletRateAdapter");

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		REAL_SOURCE_BEANS.stream()
			.filter(registry::containsBeanDefinition)
			.forEach(registry::removeBeanDefinition);
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// The registry pass already removed the real adapters, before anything was instantiated.
	}

	@Bean
	PlazoFijoSource stubPlazoFijoSource() {
		return () -> {
			StubSources.PLAZO_FIJO_CALLS.incrementAndGet();
			return new PlazoFijoReport(IngestFixtures.officialRates(), IngestFixtures.bankRates(), SourceStatus.OK,
					List.of());
		};
	}

	@Bean
	FundSource stubFundSource() {
		return new FundSource() {

			@Override
			public List<FundQuote> fetchLatest() {
				StubSources.FUND_CALLS.incrementAndGet();
				return IngestFixtures.fundLatest();
			}

			@Override
			public List<FundQuote> fetchPrevious() {
				return IngestFixtures.fundPrevious();
			}
		};
	}

	@Bean
	CryptoYieldSource stubCryptoYieldSource() {
		return () -> {
			int call = StubSources.CRYPTO_CALLS.incrementAndGet();
			if (StubSources.CRYPTO_FAILS_ONCE && call == 1) {
				throw new IllegalStateException("stub crypto source exploded");
			}
			if (StubSources.CRYPTO_SOURCE_FAILURE) {
				throw new SourceException(ArgentinaDatosCryptoAdapter.SOURCE, "stub crypto source is down", null);
			}
			return IngestFixtures.cryptoYields();
		};
	}

	@Bean
	MacroSource stubMacroSource() {
		return new MacroSource() {

			@Override
			public boolean supports(MacroVariable variable) {
				return true;
			}

			@Override
			public List<MacroObservation> fetch(MacroVariable variable) {
				StubSources.MACRO_CALLS.incrementAndGet();
				return IngestFixtures.macro(variable);
			}
		};
	}

	@Bean
	DollarSource stubDollarSource() {
		return () -> {
			StubSources.DOLLAR_CALLS.incrementAndGet();
			return IngestFixtures.dollar();
		};
	}

	@Bean
	WalletRateSource stubUalaWalletSource() {
		return () -> {
			StubSources.UALA_CALLS.incrementAndGet();
			return List.of(IngestFixtures.uala());
		};
	}

	@Bean
	WalletRateSource stubCuratedWalletSource() {
		return () -> {
			StubSources.CURATED_CALLS.incrementAndGet();
			return new ArrayList<>(IngestFixtures.curated());
		};
	}
}
