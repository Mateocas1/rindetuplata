package ar.rindetuplata.source.fund;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class FundSourcesConfiguration {

    @Bean
    ArgentinaDatosFundAdapter argentinaDatosFundAdapter(SourcesProperties properties) {
        return new ArgentinaDatosFundAdapter(SourceClients.create(properties, properties.argentinaDatosUrl()),
                Clock.systemUTC());
    }
}
