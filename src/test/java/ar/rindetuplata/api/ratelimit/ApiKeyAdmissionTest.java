package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.apikey.ApiKeyPlan;
import ar.rindetuplata.apikey.ApiKeyPrincipal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What a presented key is worth. An unknown or revoked key is refused before any limit; a valid one
 * has a bucket per key and plan and a quota per Argentine day, and a request the bucket refuses never counts
 * towards the quota.
 */
class ApiKeyAdmissionTest {

	private static final String BUCKET_DETAIL = "Too many requests for this API key; try again later";

	private static final String QUOTA_DETAIL =
			"The daily quota of this API key is used up; it resets at midnight (America/Argentina/Buenos_Aires)";

	/** 2026-10-20T09:00 in Buenos Aires: 15 hours to midnight. */
	private static final long SECONDS_TO_MIDNIGHT = 15 * 3600;

	private final TestClock clock = new TestClock();

	private final Map<String, ApiKeyPrincipal> keys = new HashMap<>();

	private ApiKeyAdmission admission(ApiPlanLimits free, ApiPlanLimits partner) {
		return new ApiKeyAdmission(presented -> Optional.ofNullable(keys.get(presented)),
				Map.of(ApiKeyPlan.FREE, free, ApiKeyPlan.PARTNER, partner), 1000, clock);
	}

	private ApiKeyAdmission admission(int capacity, double refill, long dailyQuota) {
		ApiPlanLimits limits = new ApiPlanLimits(capacity, refill, dailyQuota);
		return admission(limits, new ApiPlanLimits(capacity * 2, refill, dailyQuota * 2));
	}

	private void key(String presented, long id, ApiKeyPlan plan) {
		keys.put(presented, new ApiKeyPrincipal(id, plan));
	}

	@Test
	void anUnknownKeyIsRefusedAndTakesNothingFromAnyBucket() {
		ApiKeyAdmission admission = admission(1, 0.5, 100);
		key("good", 1, ApiKeyPlan.FREE);

		assertThat(admission.admit("rtp_unknown")).isEqualTo(new ApiKeyAdmission.Unknown());
		assertThat(admission.admit("rtp_unknown")).isEqualTo(new ApiKeyAdmission.Unknown());

		assertThat(admission.admit("good")).as("the good key's bucket is untouched")
				.isEqualTo(new ApiKeyAdmission.Admitted());
	}

	@Test
	void aValidKeyHasABucketOfItsPlanAndTheLimitedAnswerSaysWhenToRetry() {
		ApiKeyAdmission admission = admission(2, 0.5, 100);
		key("free", 1, ApiKeyPlan.FREE);

		assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Admitted());
		assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Admitted());
		assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Limited(2, BUCKET_DETAIL));

		clock.advance(Duration.ofSeconds(2));
		assertThat(admission.admit("free")).as("one token is back").isEqualTo(new ApiKeyAdmission.Admitted());
	}

	@Test
	void everyKeyHasItsOwnBucketAndThePartnerPlanHasTheBiggerOne() {
		ApiKeyAdmission admission = admission(2, 0.5, 100);
		key("free-a", 1, ApiKeyPlan.FREE);
		key("free-b", 2, ApiKeyPlan.FREE);
		key("partner", 3, ApiKeyPlan.PARTNER);
		admission.admit("free-a");
		admission.admit("free-a");

		assertThat(admission.admit("free-a")).isInstanceOf(ApiKeyAdmission.Limited.class);
		assertThat(admission.admit("free-b")).isEqualTo(new ApiKeyAdmission.Admitted());
		for (int i = 0; i < 4; i++) {
			assertThat(admission.admit("partner")).as("partner request %d", i)
					.isEqualTo(new ApiKeyAdmission.Admitted());
		}
		assertThat(admission.admit("partner")).isInstanceOf(ApiKeyAdmission.Limited.class);
	}

	@Test
	void theDailyQuotaRunsOutUntilMidnightInBuenosAiresThenStartsOver() {
		ApiKeyAdmission admission = admission(100, 100, 3);
		key("free", 1, ApiKeyPlan.FREE);
		key("other", 2, ApiKeyPlan.FREE);
		for (int i = 0; i < 3; i++) {
			assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Admitted());
		}

		assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Limited(SECONDS_TO_MIDNIGHT, QUOTA_DETAIL));
		assertThat(admission.admit("other")).as("another key has its own quota")
				.isEqualTo(new ApiKeyAdmission.Admitted());

		clock.advance(Duration.ofSeconds(SECONDS_TO_MIDNIGHT - 1));
		assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Limited(1, QUOTA_DETAIL));
		clock.advance(Duration.ofSeconds(1));
		assertThat(admission.admit("free")).as("a new Argentine day").isEqualTo(new ApiKeyAdmission.Admitted());
	}

	@Test
	void aRequestTheBucketRefusesNeverCountsTowardsTheQuota() {
		ApiKeyAdmission admission = admission(1, 1, 2);
		key("free", 1, ApiKeyPlan.FREE);
		assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Admitted());
		for (int i = 0; i < 5; i++) {
			assertThat(admission.admit("free")).isEqualTo(new ApiKeyAdmission.Limited(1, BUCKET_DETAIL));
		}

		clock.advance(Duration.ofSeconds(1));
		assertThat(admission.admit("free")).as("the second counted request").isEqualTo(new ApiKeyAdmission.Admitted());
		clock.advance(Duration.ofSeconds(1));

		assertThat(admission.admit("free")).as("the quota of 2 is used up, not the bucket")
				.isEqualTo(new ApiKeyAdmission.Limited(SECONDS_TO_MIDNIGHT - 2, QUOTA_DETAIL));
	}

	@Test
	void theQuotaOfAPartnerIsItsOwn() {
		ApiKeyAdmission admission = admission(new ApiPlanLimits(100, 100, 1), new ApiPlanLimits(100, 100, 3));
		key("free", 1, ApiKeyPlan.FREE);
		key("partner", 2, ApiKeyPlan.PARTNER);
		admission.admit("free");
		assertThat(admission.admit("free")).isInstanceOf(ApiKeyAdmission.Limited.class);

		for (int i = 0; i < 3; i++) {
			assertThat(admission.admit("partner")).isEqualTo(new ApiKeyAdmission.Admitted());
		}
		assertThat(admission.admit("partner")).isInstanceOf(ApiKeyAdmission.Limited.class);
	}
}
