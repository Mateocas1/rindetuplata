package ar.rindetuplata.alert;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The alert-rules API of T6c (S3, S13): {@code GET/POST /api/v1/alerts} and {@code DELETE /api/v1/alerts/{id}},
 * all of them for the authenticated email only. Security (T6a) already requires a login on {@code /api/v1/alerts/**}
 * and a CSRF token on every state-changing request.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

	private final AlertService alerts;

	public AlertController(AlertService alerts) {
		this.alerts = alerts;
	}

	@GetMapping
	List<AlertResponse> list(Authentication authentication) {
		return alerts.list(authentication.getName());
	}

	@PostMapping
	ResponseEntity<AlertResponse> create(@RequestBody AlertRequest request, Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(alerts.create(authentication.getName(), request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable long id, Authentication authentication) {
		alerts.delete(authentication.getName(), id);
		return ResponseEntity.noContent().build();
	}

}
