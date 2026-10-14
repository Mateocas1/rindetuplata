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
}
