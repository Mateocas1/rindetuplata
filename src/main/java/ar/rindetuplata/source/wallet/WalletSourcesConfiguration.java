package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class WalletSourcesConfiguration {

    @Bean
    UalaWalletAdapter ualaWalletAdapter(SourcesProperties properties) {
        return new UalaWalletAdapter(SourceClients.create(properties, properties.ualaUrl()), Clock.systemUTC());
    }

    @Bean
    CuratedWalletRateAdapter curatedWalletRateAdapter() {
        return new CuratedWalletRateAdapter(new ClassPathResource("curated-wallet-rates.json"), Clock.systemUTC());
    }

    @Bean
    FciBackedWalletCatalog fciBackedWalletCatalog() {
        return FciBackedWalletCatalog.fromClasspath();
    }
}
