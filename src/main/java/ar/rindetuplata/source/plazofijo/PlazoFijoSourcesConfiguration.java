package ar.rindetuplata.source.plazofijo;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class PlazoFijoSourcesConfiguration {

    @Bean
    BcraPlazoFijoAdapter bcraPlazoFijoAdapter(SourcesProperties properties) {
        return new BcraPlazoFijoAdapter(SourceClients.create(properties, properties.bcraTransparencyUrl()), Clock.systemUTC());
    }

    @Bean
    ArgentinaDatosPlazoFijoAdapter argentinaDatosPlazoFijoAdapter(SourcesProperties properties) {
        return new ArgentinaDatosPlazoFijoAdapter(SourceClients.create(properties, properties.argentinaDatosUrl()),
                Clock.systemUTC());
    }

    @Bean
    @Primary
    PlazoFijoSource plazoFijoSource(BcraPlazoFijoAdapter official, ArgentinaDatosPlazoFijoAdapter banks) {
        return new CompositePlazoFijoSource(official, banks);
    }
}
