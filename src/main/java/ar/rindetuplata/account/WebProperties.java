package ar.rindetuplata.account;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code rindetuplata.web.*}: the browser origins allowed to call the API with credentials (CORS, SPA). */
@ConfigurationProperties("rindetuplata.web")
public record WebProperties(@DefaultValue("http://localhost:4321") String allowedOrigins) {

	/** The configured origins as a list; commas separate them and blanks are dropped. */
	public List<String> allowedOriginList() {
		return Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty()).toList();
	}
}
