package ar.rindetuplata.source.wallet;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class WalletSourcesConfiguration {

    @Bean
    UalaWalletAdapter ualaWalletAdapter(SourcesProperties properties) {
        return new UalaWalletAdapter(SourceClients.create(properties, properties.ualaUrl()), Clock.systemUTC());
    }

    /** The curated rates come from the V4 table, not from the retired classpath seed (T6b, S12). */
    @Bean
    CuratedWalletRateAdapter curatedWalletRateAdapter(JdbcClient jdbc, Clock clock) {
        return new CuratedWalletRateAdapter(jdbc, clock);
    }

    @Bean
    FciBackedWalletCatalog fciBackedWalletCatalog() {
        return FciBackedWalletCatalog.fromClasspath();
    }
}
