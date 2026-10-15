package ar.rindetuplata.account;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * T6a security (S4, S13): one chain for the API, magic-link login over Spring Security's JDBC one-time-token
 * service, JDBC sessions, SPA CSRF, CORS with credentials, and JSON {@code ProblemDetail} for 401/403. Public
 * reads stay open; the authorities of a session are recomputed on every request ({@link RolesRefreshFilter});
 * {@code /api/v1/me} and {@code /api/v1/alerts/**} need a login; {@code /api/v1/admin/**} needs
 * {@code ROLE_ADMIN}; everything else is denied.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({ AccountProperties.class, WebProperties.class, MailProperties.class })
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
			SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
			AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler,
			AccountRoles accountRoles) throws Exception {
		http
				.addFilterAfter(new RolesRefreshFilter(accountRoles), SecurityContextHolderFilter.class)
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
				.securityContext(context -> context
						.securityContextRepository(securityContextRepository)
						.requireExplicitSave(true))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.requestCache(RequestCacheConfigurer::disable)
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.GET, "/api/v1/ranking", "/api/v1/simulations",
								"/api/v1/instruments", "/api/v1/instruments/**").permitAll()
						.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers("/api/v1/me", "/api/v1/alerts/**").authenticated()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.anyRequest().denyAll())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(logout -> logout
						.logoutUrl("/api/v1/auth/logout")
						.logoutSuccessHandler((request, response, authentication) ->
								response.setStatus(HttpServletResponse.SC_NO_CONTENT)));
		return http.build();
	}

	/**
	 * Spring Security's one-time-token service over the {@code one_time_tokens} table (V3). The shared clock makes
	 * the 15-minute lifetime of {@code rindetuplata.auth.token-ttl} assertable in tests.
	 */
	@Bean
	OneTimeTokenService oneTimeTokenService(JdbcTemplate jdbcTemplate, Clock clock) {
		JdbcOneTimeTokenService service = new JdbcOneTimeTokenService(jdbcTemplate);
		service.setClock(clock);
		return service;
	}

	/**
	 * The SPA CSRF cookie ({@code XSRF-TOKEN}, readable by the frontend), shared with the login strategy.
	 * {@code CookieCsrfTokenRepository} already marks it {@code Secure} on a secure request (T7a,
	 * {@code server.forward-headers-strategy=native} makes Caddy's terminated TLS visible); the customizer adds the
	 * {@code rindetuplata.auth.secure-cookie} switch and pins {@code SameSite=Lax}, so the cookie travels on a
	 * credentialed same-site {@code fetch} and never on a cross-site one.
	 */
	@Bean
	CsrfTokenRepository csrfTokenRepository(AccountProperties properties) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> {
			cookie.sameSite("Lax");
			if (properties.secureCookie()) {
				cookie.secure(true);
			}
		});
		return repository;
	}

	/**
	 * What a magic-link login does to the session, as Spring Security's own login filters do: a new session id
	 * (no session fixation) and a new CSRF token.
	 */
	@Bean
	SessionAuthenticationStrategy loginSessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	/** The session store of the API: Spring Security writes and reads the context here. */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * The session cookie: HttpOnly, SameSite=Lax, and {@code Secure} only when
	 * {@code rindetuplata.auth.secure-cookie} says so (false for local HTTP, true on the VPS).
	 */
	@Bean
	DefaultCookieSerializer cookieSerializer(AccountProperties properties) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("SESSION");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(properties.secureCookie());
		return serializer;
	}

	/** CORS with credentials for the frontend origins of {@code rindetuplata.web.allowed-origins}. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(WebProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOriginList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Requested-With", "Accept", "Origin"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
