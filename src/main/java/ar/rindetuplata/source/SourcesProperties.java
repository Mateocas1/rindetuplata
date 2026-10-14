package ar.rindetuplata.source;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code rindetuplata.sources.*}: HTTP behaviour and the base URL of every external source. */
@ConfigurationProperties("rindetuplata.sources")
public record SourcesProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout,
        @DefaultValue("rindetuplata (+https://github.com/Mateocas1/rindetuplata)") String userAgent,
        @DefaultValue("https://api.bcra.gob.ar/transparencia/v1.0") String bcraTransparencyUrl,
        @DefaultValue("https://api.bcra.gob.ar/estadisticas/v4.0") String bcraStatisticsUrl,
        @DefaultValue("https://api.argentinadatos.com/v1/finanzas") String argentinaDatosUrl,
        @DefaultValue("https://dolarapi.com/v1") String dolarApiUrl,
        @DefaultValue("https://www.uala.com.ar") String ualaUrl,
        @DefaultValue("24") int macroLimit) {
}
