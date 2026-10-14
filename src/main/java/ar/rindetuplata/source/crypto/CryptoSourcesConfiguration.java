package ar.rindetuplata.source.crypto;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class CryptoSourcesConfiguration {

    @Bean
    ArgentinaDatosCryptoAdapter argentinaDatosCryptoAdapter(SourcesProperties properties) {
        return new ArgentinaDatosCryptoAdapter(SourceClients.create(properties, properties.argentinaDatosUrl()),
                Clock.systemUTC());
    }
}
