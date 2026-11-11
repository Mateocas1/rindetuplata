package ar.rindetuplata.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.wallet.FundClassKey;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Name rules: fund identity and the two exclusions are decided from the class name alone. */
class FundClassRulesTest {

	@Test
	void fundIdentityIsTheNameBeforeTheLastClassSuffix() {
		assertThat(FundClassKey.fundOf("Delta Pesos - Clase A")).isEqualTo("delta pesos");
		assertThat(FundClassKey.fundOf("Delta  Pesos - CLASE B")).isEqualTo("delta pesos");
		assertThat(FundClassKey.fundOf("Fondo - Clase X - Clase A")).isEqualTo("fondo - clase x");
		assertThat(FundClassKey.fundOf("Fondo Fiscal - Clase L Ley N° 27.743")).isEqualTo("fondo fiscal");
		assertThat(FundClassKey.fundOf("Fondo Solo")).isEqualTo("fondo solo");
		assertThat(FundClassKey.fundOf("Fondo Clasetta")).isEqualTo("fondo clasetta");
	}

	@Test
	void foreignCurrencyIsAWholeWordOfTheFundNameInAnyCaseOrAccent() {
		assertThat(FundClassRules.exclusion("Balanz Money Market USD - Clase A"))
				.contains(ExcludedReason.FOREIGN_CURRENCY);
		assertThat(FundClassRules.exclusion("Cocos Dólar Money Market - Clase A"))
				.contains(ExcludedReason.FOREIGN_CURRENCY);
		assertThat(FundClassRules.exclusion("Delta Dólares - Clase A")).contains(ExcludedReason.FOREIGN_CURRENCY);
		assertThat(FundClassRules.exclusion("Fondo DOLARES Ahorro - Clase A"))
				.contains(ExcludedReason.FOREIGN_CURRENCY);
		assertThat(FundClassRules.exclusion("Fondo U$S - Clase A")).contains(ExcludedReason.FOREIGN_CURRENCY);
		assertThat(FundClassRules.exclusion("Delta Pesos - Clase A")).isEqualTo(Optional.empty());
		assertThat(FundClassRules.exclusion("Fondo Husdon - Clase A")).isEqualTo(Optional.empty());
		assertThat(FundClassRules.exclusion("Fondo Dolarizado - Clase A")).isEqualTo(Optional.empty());
	}

	@Test
	void taxAmnestyClassesAreRestrictedInEverySpelling() {
		assertThat(FundClassRules.exclusion("Fondo - Clase L Ley N° 27.743")).contains(ExcludedReason.RESTRICTED_CLASS);
		assertThat(FundClassRules.exclusion("Fondo - Clase K Ley Nº 27.743")).contains(ExcludedReason.RESTRICTED_CLASS);
		assertThat(FundClassRules.exclusion("Fondo - Clase Ley N° 27.743")).contains(ExcludedReason.RESTRICTED_CLASS);
		assertThat(FundClassRules.exclusion("Fondo - Clase Ley 27743")).contains(ExcludedReason.RESTRICTED_CLASS);
		assertThat(FundClassRules.exclusion("Fondo - Clase A")).isEqualTo(Optional.empty());
		assertThat(FundClassRules.exclusion("Fondo - Clase Ley N° 27.744")).isEqualTo(Optional.empty());
	}
}
