package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.domain.Money;
import ar.rindetuplata.source.ValidationRules;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T5a ranking input defaults and validation (T5 design notes). */
class RankingQueryTest {

	@Test
	void defaultsMatchTheProductDefaults() {
		RankingQuery query = RankingQuery.defaults();

		assertThat(query.amount()).isEqualTo(Money.ars("1000000"));
		assertThat(query.days()).isEqualTo(30);
		assertThat(query.clientOf()).isEmpty();
		assertThat(query.kinds()).containsExactlyInAnyOrder(RankedKind.values());
		assertThat(query.asOf()).isEqualTo(LocalDate.now(ValidationRules.ARGENTINA));
		assertThat(query.limit()).isEqualTo(50);
	}

	@Test
	void explicitQueryKeepsItsInputs() {
		RankingQuery query = new RankingQuery(Money.ars("500000"), 60, Set.of(4L), Set.of(RankedKind.WALLET),
				LocalDate.parse("2026-10-14"), 200);

		assertThat(query.amount()).isEqualTo(Money.ars("500000"));
		assertThat(query.days()).isEqualTo(60);
		assertThat(query.clientOf()).containsExactly(4L);
		assertThat(query.kinds()).containsExactly(RankedKind.WALLET);
		assertThat(query.asOf()).isEqualTo(LocalDate.parse("2026-10-14"));
		assertThat(query.limit()).isEqualTo(200);
	}

	@Test
	void nullClientOfAndKindsFallBackToTheDefaults() {
		RankingQuery query = new RankingQuery(Money.ars("1000000"), 30, null, null, null, 50);

		assertThat(query.clientOf()).isEmpty();
		assertThat(query.kinds()).containsExactlyInAnyOrder(RankedKind.values());
		assertThat(query.asOf()).isEqualTo(LocalDate.now(ValidationRules.ARGENTINA));
	}

	@Test
	void rejectsNonPositiveAmountAndDays() {
		assertThatThrownBy(() -> new RankingQuery(Money.ars("0"), 30, Set.of(), all(), LocalDate.parse("2026-10-14"),
				50)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RankingQuery(Money.ars("1000000"), 0, Set.of(), all(),
				LocalDate.parse("2026-10-14"), 50)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsALimitOutsideOneToTwoHundred() {
		assertThatThrownBy(() -> new RankingQuery(Money.ars("1000000"), 30, Set.of(), all(),
				LocalDate.parse("2026-10-14"), 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RankingQuery(Money.ars("1000000"), 30, Set.of(), all(),
				LocalDate.parse("2026-10-14"), 201)).isInstanceOf(IllegalArgumentException.class);
	}

	private static Set<RankedKind> all() {
		return Set.copyOf(List.of(RankedKind.values()));
	}
}
