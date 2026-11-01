package ar.rindetuplata.api.ranking;

import ar.rindetuplata.ranking.PublicRanking;
import ar.rindetuplata.ranking.RankedKind;
import ar.rindetuplata.ranking.RankingQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The personalized ranking of the public API (S15): {@code GET /api/v1/ranking} simulates every instrument for the
 * user's amount and days, sorted by final amount, and returns the request echo, the inflation the real return was
 * measured against (S10) and every instrument it could not use with its reason. Read-only; every row states its
 * source and dates (S11). By default the fund classes of one fund are grouped into a single row (the best class for
 * the request, with {@code foldedClasses} counting the siblings); {@code groupFunds=false} lists every class.
 *
 * <p>The answer is cached in process per data version and resolved query ({@link PublicRanking}) and carries a weak
 * {@code ETag} with {@code Cache-Control: public, no-cache} (T3a, S3): a matching {@code If-None-Match} is a
 * {@code 304} that does no ranking work (see {@link ConditionalResponses}).
 */
@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

	private final PublicRanking ranking;

	private final Clock clock;

	public RankingController(PublicRanking ranking, Clock clock) {
		this.ranking = ranking;
		this.clock = clock;
	}

	@GetMapping
	@Operation(summary = "Personalized ranking", description = ConditionalResponses.DESCRIPTION,
			responses = {
					@ApiResponse(responseCode = "200", description = "The ranking.", headers = {
							@Header(name = "ETag", description = ConditionalResponses.ETAG_DESCRIPTION),
							@Header(name = "Cache-Control", description = ConditionalResponses.CACHE_DESCRIPTION) }),
					@ApiResponse(responseCode = "304", description = ConditionalResponses.NOT_MODIFIED_DESCRIPTION,
							content = @Content),
					@ApiResponse(responseCode = "400", description = "A parameter is out of range.") })
	ResponseEntity<RankingResponse> ranking(
			@RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) Integer days,
			@RequestParam(required = false) List<Long> clientOf,
			@RequestParam(required = false) List<RankedKind> kind,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Boolean groupFunds,
			@RequestHeader HttpHeaders headers) {
		RankingQuery query = RankingRequest.query(amount, days, clientOf, kind, asOf, limit, groupFunds, clock);
		String version = ranking.dataVersion();
		String etag = ConditionalResponses.etag(version, query.cacheKey());
		if (ConditionalResponses.matches(headers.getIfNoneMatch(), etag)) {
			return ConditionalResponses.notModified(etag);
		}
		return ConditionalResponses.ok(etag, RankingApiPresenter.ranking(ranking.rank(version, query)));
	}
}
