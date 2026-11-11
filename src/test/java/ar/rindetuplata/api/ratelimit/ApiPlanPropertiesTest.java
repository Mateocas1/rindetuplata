package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The plans in configuration and the numbers they default to. */
class ApiPlanPropertiesTest {

	private static ApiPlanProperties bind(Map<String, String> properties) {
		return new Binder(new MapConfigurationPropertySource(properties))
				.bindOrCreate("rindetuplata.api.plans", ApiPlanProperties.class);
	}

	@Test
	void theDefaultsAreTheNumbersOfTheDesign() {
		ApiPlanProperties plans = bind(Map.of());

		assertThat(plans.freeLimits()).isEqualTo(new ApiPlanLimits(600, 10, 20_000));
		assertThat(plans.partnerLimits()).isEqualTo(new ApiPlanLimits(3_000, 50, 500_000));
	}

	@Test
	void everyFieldOfEveryPlanCanBeOverriddenAlone() {
		ApiPlanProperties plans = bind(Map.of(
				"rindetuplata.api.plans.free.capacity", "5",
				"rindetuplata.api.plans.partner.daily-quota", "99",
				"rindetuplata.api.plans.partner.refill-per-second", "0.5"));

		assertThat(plans.freeLimits()).isEqualTo(new ApiPlanLimits(5, 10, 20_000));
		assertThat(plans.partnerLimits()).isEqualTo(new ApiPlanLimits(3_000, 0.5, 99));
	}

	@Test
	void aPlanWithoutRoomForARequestIsRejected() {
		assertThatThrownBy(() -> bind(Map.of("rindetuplata.api.plans.free.capacity", "0")).freeLimits())
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> bind(Map.of("rindetuplata.api.plans.free.daily-quota", "0")).freeLimits())
				.isInstanceOf(IllegalArgumentException.class);
	}
}
