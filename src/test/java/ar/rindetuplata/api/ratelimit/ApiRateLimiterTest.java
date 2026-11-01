package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** T1a: the token bucket behind the per-IP limit of the public read API, including the sweep that bounds memory. */
class ApiRateLimiterTest {

	private final TestClock clock = new TestClock();

	private ApiRateLimiter limiter;

	@BeforeEach
	void setUp() {
		limiter = new ApiRateLimiter(2, 0.5, clock);
	}

	@Test
	void allowsTheCapacityThenReportsSecondsUntilATokenIsBack() {
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();

		assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(OptionalLong.of(2));

		clock.advance(Duration.ofSeconds(1));
		assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(OptionalLong.of(1));

		clock.advance(Duration.ofSeconds(1));
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();
	}

	@Test
	void aRefusedRequestDoesNotConsumeATokenAndAnotherAddressIsIndependent() {
		limiter.tryAcquire("1.1.1.1");
		limiter.tryAcquire("1.1.1.1");
		for (int i = 0; i < 10; i++) {
			assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();
		}
		clock.advance(Duration.ofSeconds(2));

		assertThat(limiter.tryAcquire("1.1.1.1")).as("one token refilled, not debited by the refusals").isEmpty();
		assertThat(limiter.tryAcquire("2.2.2.2")).isEmpty();
	}

	@Test
	void theBucketNeverHoldsMoreThanItsCapacity() {
		clock.advance(Duration.ofHours(1));

		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();
	}

	@Test
	void idleBucketsAreSweptSoMemoryStaysBounded() {
		for (int i = 0; i < 100; i++) {
			limiter.tryAcquire("10.0.0." + i);
		}
		assertThat(limiter.trackedAddresses()).isEqualTo(100);

		clock.advance(Duration.ofMinutes(1));
		limiter.tryAcquire("2.2.2.2");

		assertThat(limiter.trackedAddresses()).as("only the bucket that is not full again remains").isEqualTo(1);
	}

	@Test
	void aNonPositiveCapacityOrRefillIsRejected() {
		assertThatThrownBy(() -> new ApiRateLimiter(0, 1, clock)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ApiRateLimiter(1, 0, clock)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void thePropertiesDefaultToACapacityOf120AndTwoTokensPerSecondWithTheBypassDisabled() {
		ApiRateLimitProperties properties = new Binder(new MapConfigurationPropertySource())
				.bindOrCreate("rindetuplata.api.rate-limit", ApiRateLimitProperties.class);

		assertThat(properties.capacity()).isEqualTo(120);
		assertThat(properties.refillPerSecond()).isEqualTo(2.0);
		assertThat(properties.bypassToken()).isEmpty();
	}
}
