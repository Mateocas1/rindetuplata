package ar.rindetuplata.api.ranking;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.ranking.InstrumentNotFoundException;
import ar.rindetuplata.ranking.RankingService;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single-instrument simulator of the public API (S3, S15): {@code GET /api/v1/simulations} answers one entry
 * for the user's amount and term, or the reason the instrument is ineligible or excluded, under a {@code status}
 * discriminator. {@code client} offers the S19 "soy cliente de…" personalization. An unknown id is a 404
 * {@code ProblemDetail}.
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

	private final RankingService ranking;

	public SimulationController(RankingService ranking) {
		this.ranking = ranking;
	}

	@GetMapping
	SimulationResponse simulate(
			@RequestParam long instrumentId,
			@RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) Integer days,
			@RequestParam(required = false, defaultValue = "false") boolean client) {
		try {
			return RankingApiPresenter.simulation(ranking.simulate(instrumentId, RankingRequest.amount(amount),
					RankingRequest.days(days), client));
		}
		catch (InstrumentNotFoundException e) {
			throw ApiException.notFound("instrumentId", e.getMessage());
		}
	}
}
