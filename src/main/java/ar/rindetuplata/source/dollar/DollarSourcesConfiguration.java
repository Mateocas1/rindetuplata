package ar.rindetuplata.source.dollar;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class DollarSourcesConfiguration {

    @Bean
    DolarApiAdapter dolarApiAdapter(SourcesProperties properties) {
        return new DolarApiAdapter(SourceClients.create(properties, properties.dolarApiUrl()), Clock.systemUTC());
    }

    /** The ArgentinaDatos MEP series the backfill reads: not a {@link DollarSource}, so the daily job ignores it. */
    @Bean
    ArgentinaDatosDollarAdapter argentinaDatosDollarAdapter(SourcesProperties properties) {
        return new ArgentinaDatosDollarAdapter(SourceClients.create(properties, properties.argentinaDatosCotizacionesUrl()),
                Clock.systemUTC());
    }
}
