package ar.rindetuplata.alert;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The T6d topology (S4): a durable topic exchange for the alert events, the durable email queue that consumes them,
 * and a dead-letter exchange with its own queue so a message the consumer finally rejects is kept for the operator
 * instead of looping. Boot's {@code RabbitAdmin} declares every one of these beans on the first connection, so the
 * broker and the application can never disagree about the names.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ OutboxProperties.class, AlertMailProperties.class })
class OutboxConfiguration {

	/**
	 * T11b: a failure the consumer marks as final ({@link AmqpRejectAndDontRequeueException}, e.g. an unreadable
	 * payload) is not retried: the container's retry advice would otherwise repeat it three times before the
	 * dead-lettering, which only delays the same outcome. The listener wraps the exception, so the whole cause chain
	 * is checked.
	 */
	@Bean
	RabbitListenerRetrySettingsCustomizer finalFailuresAreNotRetried() {
		return settings -> settings.setExceptionPredicate(failure -> {
			for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
				if (cause instanceof AmqpRejectAndDontRequeueException) {
					return false;
				}
			}
			return true;
		});
	}

	@Bean
	TopicExchange alertEventsExchange() {
		return new TopicExchange(AlertEvents.EXCHANGE, true, false);
	}

	@Bean
	TopicExchange alertEventsDeadLetterExchange() {
		return new TopicExchange(AlertEvents.DEAD_LETTER_EXCHANGE, true, false);
	}

	@Bean
	Queue alertEmailQueue() {
		return QueueBuilder.durable(AlertEvents.QUEUE)
			.deadLetterExchange(AlertEvents.DEAD_LETTER_EXCHANGE)
			.deadLetterRoutingKey(AlertEvents.DEAD_LETTER_ROUTING_KEY)
			.build();
	}

	@Bean
	Queue alertEmailDeadLetterQueue() {
		return QueueBuilder.durable(AlertEvents.DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Binding alertEmailBinding(Queue alertEmailQueue, TopicExchange alertEventsExchange) {
		return BindingBuilder.bind(alertEmailQueue).to(alertEventsExchange).with(AlertEvents.ROUTING_KEY);
	}

	@Bean
	Binding alertEmailDeadLetterBinding(Queue alertEmailDeadLetterQueue, TopicExchange alertEventsDeadLetterExchange) {
		return BindingBuilder.bind(alertEmailDeadLetterQueue)
			.to(alertEventsDeadLetterExchange)
			.with(AlertEvents.DEAD_LETTER_ROUTING_KEY);
	}

}
