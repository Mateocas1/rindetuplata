package ar.rindetuplata.ingest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

/**
 * The admin endpoint of the history backfill (T4a, L33 a): {@code POST /api/v1/admin/backfill?from=&to=}. It lives under
 * {@code /api/v1/admin/**}, which the security configuration reserves for {@code ROLE_ADMIN} (session and CSRF); it is
 * never public. 202 carries the job id, a 409 {@code ProblemDetail} says a run is still going, and an invalid range is
 * the shared 400 {@code ProblemDetail} that names {@code from} or {@code to}.
 */
@RestController
@RequestMapping("/api/v1/admin/backfill")
class BackfillController {

	private final BackfillService backfill;

	BackfillController(BackfillService backfill) {
		this.backfill = backfill;
	}

	@PostMapping
	ResponseEntity<BackfillStarted> start(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ResponseEntity.accepted().body(backfill.start(from, to));
	}

	@ExceptionHandler(BackfillAlreadyRunningException.class)
	ProblemDetail onRunning(BackfillAlreadyRunningException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}
}
