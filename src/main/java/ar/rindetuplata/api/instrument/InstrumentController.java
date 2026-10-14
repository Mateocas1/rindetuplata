package ar.rindetuplata.api.instrument;

import ar.rindetuplata.store.InstrumentKind;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The instruments of the public API: the catalogue with the latest datum of each instrument, and the history of one
 * instrument over a date range. Both are read-only and state their dates (S11).
 */
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

	private final InstrumentService instruments;

	public InstrumentController(InstrumentService instruments) {
		this.instruments = instruments;
	}

	@GetMapping
	List<InstrumentSummary> list(@RequestParam(required = false) InstrumentKind kind) {
		return instruments.list(kind);
	}

	@GetMapping("/{id}/history")
	InstrumentHistory history(@PathVariable long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return instruments.history(id, from, to);
	}
}
