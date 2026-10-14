package ar.rindetuplata.api;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one time source of the public API: "today" for the default history window is the day in
 * America/Argentina/Buenos_Aires, never the machine's day. Tests replace the bean with a fixed clock.
 */
@Configuration(proxyBeanMethods = false)
public class ApiConfiguration {

	/** Every date the API defaults or reports is a day in this zone. */
	public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	@Bean
	Clock apiClock() {
		return Clock.system(ZONE);
	}
}
