package ar.rindetuplata.alert;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the alert evaluation into the daily ingestion: the listener {@code dailyIngestionJob} registers. It
 * is a configuration of its own, like the rebuild hook, so the job only names the bean. The listener reads
 * "today" from the application clock, which tests replace.
 */
@Configuration(proxyBeanMethods = false)
class AlertEvaluationConfiguration {

	@Bean
	AlertEvaluationListener alertEvaluationListener(AlertEvaluator evaluator, Clock clock) {
		return new AlertEvaluationListener(evaluator, clock);
	}

}
