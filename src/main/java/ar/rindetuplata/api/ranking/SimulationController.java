package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.domain.Money;
import ar.rindetuplata.ranking.InstrumentNotFoundException;
import ar.rindetuplata.ranking.PublicRanking;
import ar.rindetuplata.ranking.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single-instrument simulator of the public API: {@code GET /api/v1/simulations} answers one entry
 * for the user's amount and term, or the reason the instrument is ineligible or excluded, under a {@code status}
 * discriminator. {@code client} offers the "soy cliente de…" personalization. An unknown id is a 404
 * {@code ProblemDetail}.
 *
 * <p>The simulation reads only the requested instrument and carries the same weak {@code ETag} and
 * {@code Cache-Control: public, no-cache} as the ranking; a matching {@code If-None-Match} is a
 * {@code 304} before any simulation, and an unknown id is a 404 with neither header.
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

	private final RankingService ranking;

	private final PublicRanking publicRanking;

	private final Clock clock;

	public SimulationController(RankingService ranking, PublicRanking publicRanking, Clock clock) {
		this.ranking = ranking;
		this.publicRanking = publicRanking;
		this.clock = clock;
	}

	@GetMapping
	@Operation(summary = "Simulate one instrument", description = ConditionalResponses.DESCRIPTION + " "
					+ "Entries carry breakEvenMep and the response carries dollarMep. " + BreakEvenMepView.DESCRIPTION,
			responses = {
					@ApiResponse(responseCode = "200", description = "The simulation, ineligible or excluded status.",
							headers = {
									@Header(name = "ETag", description = ConditionalResponses.ETAG_DESCRIPTION),
									@Header(name = "Cache-Control",
											description = ConditionalResponses.CACHE_DESCRIPTION) }),
					@ApiResponse(responseCode = "304", description = ConditionalResponses.NOT_MODIFIED_DESCRIPTION,
							content = @Content),
					@ApiResponse(responseCode = "400", description = "A parameter is out of range."),
					@ApiResponse(responseCode = "404", description = "No ranked instrument carries this id.") })
	ResponseEntity<SimulationResponse> simulate(
			@RequestParam long instrumentId,
			@RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) Integer days,
			@RequestParam(required = false, defaultValue = "false") boolean client,
			@RequestHeader HttpHeaders headers) {
		Money money = RankingRequest.amount(amount);
		int term = RankingRequest.days(days);
		LocalDate asOf = LocalDate.now(clock);
		String version = publicRanking.dataVersion();
		String etag = ConditionalResponses.etag(version, "simulation;instrumentId=" + instrumentId + ";amount="
				+ money.value().stripTrailingZeros() + ";days=" + term + ";client=" + client + ";asOf=" + asOf);
		if (ConditionalResponses.matches(headers.getIfNoneMatch(), etag)) {
			return ConditionalResponses.notModified(etag);
		}
		try {
			return ConditionalResponses.ok(etag, RankingApiPresenter.simulation(
					ranking.simulate(instrumentId, money, term, client, asOf)));
		}
		catch (InstrumentNotFoundException e) {
			throw ApiException.notFound("instrumentId", e.getMessage());
		}
	}
}
