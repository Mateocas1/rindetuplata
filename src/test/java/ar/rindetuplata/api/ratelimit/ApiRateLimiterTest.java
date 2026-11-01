package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
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
	void ipv6AddressesOfTheSame64ShareOneBucketAndAnother64DoesNot() {
		assertThat(limiter.tryAcquire("2001:db8:0:1::1")).isEmpty();
		assertThat(limiter.tryAcquire("2001:db8:0:1:ffff:ffff:ffff:ffff")).isEmpty();

		assertThat(limiter.tryAcquire("2001:db8:0:1:abcd::7")).as("same /64, bucket already empty").isPresent();
		assertThat(limiter.tryAcquire("2001:DB8:0:1:0:0:0:2")).as("same /64, other spelling").isPresent();
		assertThat(limiter.tryAcquire("2001:db8:0:2::1")).as("another /64 has its own bucket").isEmpty();
		assertThat(limiter.trackedAddresses()).isEqualTo(2);
	}

	@Test
	void ipv4AndIpv4MappedIpv6KeepTheFullAddress() {
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isEmpty();
		assertThat(limiter.tryAcquire("1.1.1.1")).isPresent();

		assertThat(limiter.tryAcquire("1.1.1.2")).as("a neighbour address is another client").isEmpty();
		assertThat(limiter.tryAcquire("::ffff:1.1.1.1")).as("mapped form is the same client").isPresent();
		assertThat(limiter.tryAcquire("::ffff:1.1.1.3")).as("mapped form of another client").isEmpty();
	}

	@Test
	void anUnparseableAddressFallsBackToTheRawStringWithoutAnyLookup() {
		assertThat(limiter.tryAcquire("not:an:ip:address")).isEmpty();
		assertThat(limiter.tryAcquire("not:an:ip:address")).isEmpty();
		assertThat(limiter.tryAcquire("not:an:ip:address")).isPresent();

		assertThat(limiter.tryAcquire("not:an:ip:other")).isEmpty();
		assertThat(limiter.tryAcquire("www.example.com")).isEmpty();
		assertThat(limiter.trackedAddresses()).isEqualTo(3);
	}

	@Test
	void atTheCapANewAddressIsRefusedWhileAnExistingOneIsStillServed() {
		ApiRateLimiter capped = new ApiRateLimiter(2, 0.5, 3, clock);
		for (int i = 1; i <= 3; i++) {
			assertThat(capped.tryAcquire("10.0.0." + i)).isEmpty();
		}

		OptionalLong refused = capped.tryAcquire("10.0.0.4");

		assertThat(refused).as("fail closed for an unknown client").isPresent();
		assertThat(refused.getAsLong()).isGreaterThanOrEqualTo(1);
		assertThat(capped.trackedAddresses()).isEqualTo(3);
		assertThat(capped.tryAcquire("10.0.0.1")).as("an existing address keeps working").isEmpty();
		assertThat(capped.tryAcquire("10.0.0.1")).isPresent();
		assertThat(capped.tryAcquire("10.0.0.4")).as("still refused, nothing was tracked").isPresent();
		assertThat(capped.trackedAddresses()).isEqualTo(3);
	}

	@Test
	void aSweepThatFreesFullBucketsAdmitsNewAddressesAgain() {
		ApiRateLimiter capped = new ApiRateLimiter(2, 0.5, 3, clock);
		for (int i = 1; i <= 3; i++) {
			capped.tryAcquire("10.0.0." + i);
		}
		assertThat(capped.tryAcquire("10.0.0.4")).isPresent();

		// Two seconds refill the one token each bucket spent; the scheduled sweep (every 4 s) is not due yet.
		clock.advance(Duration.ofSeconds(2));

		assertThat(capped.tryAcquire("10.0.0.4")).as("the sweep on demand freed the full buckets").isEmpty();
		assertThat(capped.trackedAddresses()).isEqualTo(1);
	}

	@Test
	void theMaximumOfTrackedAddressesMustBePositive() {
		assertThatThrownBy(() -> new ApiRateLimiter(1, 1, 0, clock)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void concurrentRequestsOfOneAddressNeverAdmitMoreThanTheCapacity() throws Exception {
		int threads = 32;
		int attemptsPerThread = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			AtomicInteger admitted = new AtomicInteger();
			List<Future<?>> done = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				done.add(pool.submit(() -> {
					start.await();
					for (int i = 0; i < attemptsPerThread; i++) {
						if (limiter.tryAcquire("1.1.1.1").isEmpty()) {
							admitted.incrementAndGet();
						}
					}
					return null;
				}));
			}
			start.countDown();
			for (Future<?> f : done) {
				f.get(30, TimeUnit.SECONDS);
			}

			assertThat(admitted.get()).as("the clock is frozen, so exactly the capacity of 2").isEqualTo(2);
		}
		finally {
			pool.shutdownNow();
		}
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
		assertThat(properties.maxTrackedAddresses()).isEqualTo(100_000);
	}

	@Test
	void textThatIsNotAnIpv6LiteralOnceTheZoneIsCutIsKeptAsGivenWithoutParsing() {
		// "abc%:" contains ':' only in its zone id; older JDKs would resolve "abc" by name, so it is never parsed.
		assertThat(ApiRateLimiter.bucketKey("abc%:")).isEqualTo("abc%:");
		assertThat(ApiRateLimiter.bucketKey("2001:db8:1:2:3:4:5:6%eth0")).isEqualTo("20010db800010002/64");
	}
}
