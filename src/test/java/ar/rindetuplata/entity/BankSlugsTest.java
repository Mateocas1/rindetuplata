package ar.rindetuplata.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * T6b slug rule of the bank entity pages: the lowercase, accent-stripped name with every run of non-alphanumerics
 * as one dash, unique across banks, and a collision takes a deterministic numeric suffix in instrument id order.
 */
class BankSlugsTest {

	@Test
	void theSlugIsTheLowercaseNameWithoutAccentsAndNonAlphanumericsAsDashes() {
		assertThat(BankSlugs.slugify("BANCO DE LA NACION ARGENTINA")).isEqualTo("banco-de-la-nacion-argentina");
		assertThat(BankSlugs.slugify("Banco Santander Río S.A.")).isEqualTo("banco-santander-rio-s-a");
		assertThat(BankSlugs.slugify("  Banco Ñandú  (Test) ")).isEqualTo("banco-nandu-test");
		assertThat(BankSlugs.slugify("BBVA--Francés/2")).isEqualTo("bbva-frances-2");
	}

	@Test
	void aNameWithNothingAlphanumericFallsBackToBanco() {
		assertThat(BankSlugs.slugify("***")).isEqualTo("banco");
		assertThat(BankSlugs.slugify("")).isEqualTo("banco");
	}

	@Test
	void distinctNamesKeepTheirPlainSlug() {
		Map<Long, String> slugs = BankSlugs.assign(List.of(new BankSlugs.Named(5, "Banco Uno"),
				new BankSlugs.Named(2, "Banco Dos")));

		assertThat(slugs).containsEntry(5L, "banco-uno").containsEntry(2L, "banco-dos");
	}

	@Test
	void aCollisionTakesASuffixInInstrumentIdOrderWhateverTheInputOrder() {
		List<BankSlugs.Named> banks = List.of(new BankSlugs.Named(9, "Banco N 2"), new BankSlugs.Named(2, "BANCO Ñ"),
				new BankSlugs.Named(1, "Banco N"), new BankSlugs.Named(4, "banco n"));

		Map<Long, String> slugs = BankSlugs.assign(banks);

		// Id 1 keeps the plain slug, ids 2 and 4 are the second and third "banco-n", and id 9 is literally
		// "banco-n-2", which a suffix already took, so it takes one of its own: unique, and the same on every run.
		assertThat(slugs).containsEntry(1L, "banco-n").containsEntry(2L, "banco-n-2").containsEntry(4L, "banco-n-3")
				.containsEntry(9L, "banco-n-2-2");
		assertThat(slugs.values()).doesNotHaveDuplicates();
		assertThat(BankSlugs.assign(banks.reversed())).isEqualTo(slugs);
	}
}
