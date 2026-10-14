package ar.rindetuplata.source.macro;

import ar.rindetuplata.source.SourceClients;
import ar.rindetuplata.source.SourcesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
class MacroSourcesConfiguration {

    @Bean
    BcraMonetariasClient bcraMonetariasClient(SourcesProperties properties) {
        return new BcraMonetariasClient(SourceClients.create(properties, properties.bcraStatisticsUrl()),
                Clock.systemUTC(), properties.macroLimit());
    }

    @Bean
    ArgentinaDatosInflationAdapter argentinaDatosInflationAdapter(SourcesProperties properties) {
        return new ArgentinaDatosInflationAdapter(SourceClients.create(properties, properties.argentinaDatosUrl()),
                Clock.systemUTC());
    }

    @Bean
    @Primary
    MacroSource macroSource(BcraMonetariasClient primary, ArgentinaDatosInflationAdapter secondary) {
        return new FallbackMacroSource(primary, secondary);
    }
}
