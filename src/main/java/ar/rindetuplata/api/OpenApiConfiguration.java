package ar.rindetuplata.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The published document: title, version and the informational, non-advice disclaimer (S8 L-G, S11). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

	static final String DESCRIPTION = """
			Informational yields for Argentine savers: plazo fijo by bank, remunerated wallets, money-market funds \
			and the dollar MEP, compared by real return against inflation. \
			This API is informational only and is not financial advice: it does not recommend any institution, \
			product or investment decision. Every row names its source id, its date of data and its validation flags.""";

	@Bean
	OpenAPI rindetuplataOpenApi() {
		return new OpenAPI().info(new Info()
				.title("rindetuplata API")
				.version("v1")
				.description(DESCRIPTION));
	}
}
