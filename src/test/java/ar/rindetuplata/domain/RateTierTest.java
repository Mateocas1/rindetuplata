package ar.rindetuplata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RateTierTest {

    @Test
    void rejectsZeroAndNegativeBoundedCeiling() {
        assertThatThrownBy(() -> RateTier.upTo(Money.ars("0"), AnnualRate.ofPercent("20")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ceiling must be positive");
        assertThatThrownBy(() -> RateTier.upTo(Money.ars("-1"), AnnualRate.ofPercent("20")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ceiling must be positive");
    }

    @Test
    void smallestPositiveCeilingIsAccepted() {
        RateTier tier = RateTier.upTo(Money.ars("0.01"), AnnualRate.ofPercent("20"));

        assertThat(tier.ceiling()).isEqualTo(Money.ars("0.01"));
    }
}
