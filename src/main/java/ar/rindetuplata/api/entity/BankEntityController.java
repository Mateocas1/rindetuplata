package ar.rindetuplata.api.entity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bank entity pages of the public API (T6b, S6): every bank with its latest plazo fijo rates, and one bank with
 * its 90-day non-client TNA series and how often it paid the highest non-client TNA. Read-only, public and under the
 * read-API rate limit; the rates state their capture and data dates (S11).
 */
@RestController
@RequestMapping("/api/v1/entities/banks")
public class BankEntityController {

	private final BankEntityService banks;

	public BankEntityController(BankEntityService banks) {
		this.banks = banks;
	}

	@GetMapping
	@Operation(summary = "Banks with today's plazo fijo rates", description = """
			Every bank that has a valid plazo fijo snapshot at or before today, sorted by slug, with its latest \
			non-client and client TNA (fractions, scale 6) and the capture and data dates of that snapshot. \
			A rate is null when the bank does not publish it.""")
	List<BankRatesView> list() {
		return banks.banks();
	}

	@GetMapping("/{slug}")
	@Operation(summary = "One bank: rates, 90-day series and best-rate days", description = """
			The bank's latest rates, its non-client TNA per capture day over the last 90 calendar days ending today \
			(inclusive, America/Argentina/Buenos_Aires, oldest first) and bestDays/observedDays: among the capture \
			days of that window on which the bank had a valid non-client TNA (observedDays), the days on which it was \
			the highest among all banks (bestDays). A tie counts for each bank tied; invalid snapshots and days the \
			bank was not captured are left out.""",
			responses = {
					@ApiResponse(responseCode = "200", description = "The bank."),
					@ApiResponse(responseCode = "404", description = "No bank has this slug.") })
	BankDetailView bank(@PathVariable String slug) {
		return banks.bank(slug);
	}
}
