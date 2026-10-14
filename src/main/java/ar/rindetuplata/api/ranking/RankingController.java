package ar.rindetuplata.api.ranking;

import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The personalized ranking of the public API (S15): {@code GET /api/v1/ranking} simulates every instrument for the
 * user's amount and days, sorted by final amount, and returns the request echo, the inflation the real return was
 * measured against (S10) and every instrument it could not use with its reason. Read-only; every row states its
 * source and dates (S11).
 */
@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

	private final RankingService ranking;

	private final Clock clock;

	public RankingController(RankingService ranking, Clock clock) {
		this.ranking = ranking;
		this.clock = clock;
	}

	@GetMapping
	RankingResponse ranking(
			@RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) Integer days,
			@RequestParam(required = false) List<Long> clientOf,
			@RequestParam(required = false) List<RankedKind> kind,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
			@RequestParam(required = false) Integer limit) {
		return RankingApiPresenter.ranking(
				ranking.rank(RankingRequest.query(amount, days, clientOf, kind, asOf, limit, clock)));
	}
}
