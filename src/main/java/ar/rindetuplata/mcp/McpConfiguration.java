package ar.rindetuplata.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The MCP server inside the API (T6e, S6, L32 c): the official MCP Java SDK, stateless Streamable HTTP at
 * {@code POST /mcp}, registered as its own servlet next to Spring MVC. Stateless means no session, no
 * {@code Mcp-Session-Id} and nothing kept between two requests, so any request can reach the single instance (S16)
 * and the endpoint needs neither CSRF nor a cookie. Spring Security still guards it: {@code SecurityConfiguration}
 * opens exactly {@code POST /mcp} and {@code ApiRateLimitFilter} limits it like the read API.
 */
@Configuration(proxyBeanMethods = false)
class McpConfiguration {

	static final String ENDPOINT = "/mcp";

	private static final String INSTRUCTIONS = """
			Argentine yield comparator (rindetuplata): where does money earn the most today, measured by real return \
			against inflation. All tools are read-only. Start with get_ranking; its rows carry an id for \
			simulate_instrument and get_instrument_history. Every result states its data date and source; quote both \
			when you answer, and say so when the data date is not today. Amounts are ARS; rates and returns are \
			fractions (0.05 is 5%). This is information, not financial advice.""";

	@Bean
	HttpServletStatelessServerTransport mcpTransport() {
		return HttpServletStatelessServerTransport.builder().messageEndpoint(ENDPOINT).build();
	}

	@Bean
	ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(
			HttpServletStatelessServerTransport transport) {
		ServletRegistrationBean<HttpServletStatelessServerTransport> registration = new ServletRegistrationBean<>(
				transport, ENDPOINT);
		registration.setName("mcp");
		return registration;
	}

	@Bean(destroyMethod = "close")
	McpStatelessSyncServer mcpServer(HttpServletStatelessServerTransport transport, ReadOnlyTools tools) {
		return McpServer.sync(transport)
				.serverInfo("rindetuplata", "0.1.0")
				.instructions(INSTRUCTIONS)
				.capabilities(ServerCapabilities.builder().tools(false).build())
				// The tools validate their arguments themselves, so an error names the argument like the REST API does.
				.validateToolInputs(false)
				.tools(tools.specifications())
				.build();
	}
}
