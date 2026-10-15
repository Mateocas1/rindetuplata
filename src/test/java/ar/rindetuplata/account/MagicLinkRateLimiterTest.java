package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T11a: the fixed-window counter behind the per-IP limit, including the sweep that bounds its memory. */
class MagicLinkRateLimiterTest {

	private final MutableClock clock = new MutableClock();

	private MagicLinkRateLimiter limiter;

	@BeforeEach
	void setUp() {
		clock.reset();
		limiter = new MagicLinkRateLimiter(2, Duration.ofMinutes(10), clock);
	}

	@Test
	void allowsTheLimitThenReportsSecondsUntilTheWindowEnds() {
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();

		clock.advance(Duration.ofSeconds(90));

		assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(OptionalLong.of(510));
	}

	@Test
	void expiredWindowsAreSweptSoMemoryStaysBounded() {
		for (int i = 0; i < 100; i++) {
			limiter.tryAcquire("10.0.0." + i);
		}
		assertThat(limiter.trackedAddresses()).isEqualTo(100);

		clock.advance(Duration.ofMinutes(11));
		limiter.tryAcquire("2.2.2.2");

		assertThat(limiter.trackedAddresses()).as("only the live window remains").isEqualTo(1);
	}

	@Test
	void aNewWindowStartsAfterTheOldOneEnds() {
		limiter.tryAcquire("1.1.1.1");
		limiter.tryAcquire("1.1.1.1");
		assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();

		clock.advance(Duration.ofMinutes(10));

		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
	}
}
