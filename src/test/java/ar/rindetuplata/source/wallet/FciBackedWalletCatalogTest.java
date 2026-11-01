package ar.rindetuplata.source.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.source.Fixtures;
import ar.rindetuplata.source.FlagCode;
import ar.rindetuplata.source.Severity;
import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FciBackedWalletCatalogTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<FundQuote> quotes =
            ArgentinaDatosFundAdapter.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-ultimo.json"), FETCHED_AT);

    /** The classes confirmed on 2026-11-01 (T5a, L24): verbatim rows of the live response, not a re-recording (L16). */
    private final List<FundQuote> confirmedClasses = ArgentinaDatosFundAdapter
            .parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-wallet-classes-2026-11-01.json"), FETCHED_AT);

    @Test
    void classpathMappingLinksMercadoPagoToMercadoFondoClaseA() {
        FciBackedWalletCatalog catalog = FciBackedWalletCatalog.fromClasspath();

        assertThat(catalog.mappings()).extracting(FciBackedWalletMapping::walletId)
                .containsExactly("mercado-pago", "personal-pay");
        assertThat(catalog.mappings().get(0).name()).isEqualTo("Mercado Pago");
        assertThat(catalog.mappings().get(0).fundClass()).isEqualTo("Mercado Fondo - Clase A");
        assertThat(catalog.mappings().get(0).aliases()).isEmpty();
    }

    @Test
    void classpathMappingLinksPersonalPayToDeltaPesosClaseX() {
        FciBackedWalletMapping personalPay = FciBackedWalletCatalog.fromClasspath().mappings().get(1);

        assertThat(personalPay.walletId()).isEqualTo("personal-pay");
        assertThat(personalPay.name()).isEqualTo("Personal Pay");
        assertThat(personalPay.fundClass()).isEqualTo("Delta Pesos - Clase X");
        assertThat(personalPay.aliases()).isEmpty();
        assertThat(personalPay.matches("DELTA  Pesos - clase x")).isTrue();
        // Same fund, other class: Personal Pay offers Clase X only.
        assertThat(personalPay.matches("Delta Pesos - Clase A")).isFalse();
    }

    @Test
    void everyClasspathMappingPointsAtAClassConfirmedInTheRecordedFundResponses() {
        // A class is mapped only once it is confirmed in a recorded response: the 2026-10-09 fixture or the
        // 2026-11-01 excerpt of the classes T5a added. Neither is re-recorded to make a mapping pass (L16).
        List<FundQuote> recorded = new ArrayList<>(quotes);
        recorded.addAll(confirmedClasses);

        List<FciBackedWallet> wallets = FciBackedWalletCatalog.fromClasspath().resolve(recorded);

        assertThat(wallets).isNotEmpty().allSatisfy(w -> assertThat(w.quote()).isNotNull());
    }

    @Test
    void resolvesPersonalPayToItsClassInTheConfirmedExcerpt() {
        FciBackedWallet personalPay = FciBackedWalletCatalog.fromClasspath().resolve(confirmedClasses).stream()
                .filter(w -> w.walletId().equals("personal-pay")).findFirst().orElseThrow();

        assertThat(personalPay.name()).isEqualTo("Personal Pay");
        assertThat(personalPay.quote().name()).isEqualTo("Delta Pesos - Clase X");
        assertThat(personalPay.quote().vcp()).isEqualByComparingTo("6064.634");
        assertThat(personalPay.quote().date()).isEqualTo(LocalDate.parse("2026-10-30"));
        assertThat(personalPay.flags()).isEmpty();
    }

    @Test
    void personalPayReadsClassNotFoundWhenItsClassIsAbsent() {
        FciBackedWallet personalPay = FciBackedWalletCatalog.fromClasspath().resolve(quotes).stream()
                .filter(w -> w.walletId().equals("personal-pay")).findFirst().orElseThrow();

        assertThat(personalPay.quote()).isNull();
        assertThat(personalPay.isInvalid()).isTrue();
        assertThat(personalPay.flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.FUND_CLASS_NOT_FOUND);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
        });
    }

    @Test
    void resolvesTheLatestQuoteOfTheMappedClass() {
        FciBackedWallet mercadoPago = FciBackedWalletCatalog.fromClasspath().resolve(quotes).get(0);

        assertThat(mercadoPago.walletId()).isEqualTo("mercado-pago");
        assertThat(mercadoPago.name()).isEqualTo("Mercado Pago");
        assertThat(mercadoPago.quote().vcp()).isEqualByComparingTo("25937.047");
        assertThat(mercadoPago.quote().date()).isEqualTo(LocalDate.parse("2026-10-09"));
        assertThat(mercadoPago.flags()).isEmpty();
        assertThat(mercadoPago.isInvalid()).isFalse();
    }

    @Test
    void unknownClassIsKeptAsInvalid() {
        String json = "{\"wallets\":[{\"id\":\"x\",\"name\":\"X\",\"fund\":\"Nope - Clase Z\"}]}";

        FciBackedWallet wallet = FciBackedWalletCatalog.fromJson(json.getBytes(StandardCharsets.UTF_8)).resolve(quotes).get(0);

        assertThat(wallet.quote()).isNull();
        assertThat(wallet.flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(FlagCode.FUND_CLASS_NOT_FOUND);
            assertThat(f.severity()).isEqualTo(Severity.INVALID);
        });
    }

    @Test
    void theClassKeyIgnoresCaseAccentsAndWhitespace() {
        // The source has no class id (L7), so the key is the normalized name.
        String json = "{\"wallets\":[{\"id\":\"x\",\"name\":\"X\",\"fund\":\"  MERCADO   FONDO - clase a \"}]}";

        FciBackedWallet wallet = FciBackedWalletCatalog.fromJson(json.getBytes(StandardCharsets.UTF_8)).resolve(quotes).get(0);

        assertThat(wallet.quote()).isNotNull();
        assertThat(wallet.quote().name()).isEqualTo("Mercado Fondo - Clase A");
        assertThat(wallet.flags()).isEmpty();
    }

    @Test
    void anAliasResolvesTheClassWhenThePrimaryNameIsGone() {
        String json = "{\"wallets\":[{\"id\":\"x\",\"name\":\"X\",\"fund\":\"Mercado Fondo Renombrado - Clase A\","
                + "\"aliases\":[\"mércado fondo - CLASE A\"]}]}";

        FciBackedWalletMapping mapping = FciBackedWalletCatalog.fromJson(json.getBytes(StandardCharsets.UTF_8)).mappings().get(0);
        FciBackedWallet wallet = FciBackedWalletCatalog.fromJson(json.getBytes(StandardCharsets.UTF_8)).resolve(quotes).get(0);

        assertThat(mapping.fundClass()).isEqualTo("Mercado Fondo Renombrado - Clase A");
        assertThat(mapping.aliases()).containsExactly("mércado fondo - CLASE A");
        assertThat(wallet.quote()).isNotNull();
        assertThat(wallet.quote().name()).isEqualTo("Mercado Fondo - Clase A");
    }

    @Test
    void deadClassPropagatesItsStaleFlag() {
        String json = "{\"wallets\":[{\"id\":\"x\",\"name\":\"X\",\"fund\":\"Mercado Fondo - Clase B\"}]}";

        FciBackedWallet wallet = FciBackedWalletCatalog.fromJson(json.getBytes(StandardCharsets.UTF_8)).resolve(quotes).get(0);

        assertThat(wallet.quote().date()).isEqualTo(LocalDate.parse("2025-05-30"));
        assertThat(wallet.flags()).extracting(f -> f.code()).containsExactly(FlagCode.STALE);
    }

    @Test
    void malformedMappingIsASourceException() {
        assertThatThrownBy(() -> FciBackedWalletCatalog.fromJson("{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> FciBackedWalletCatalog.fromJson("[".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> FciBackedWalletCatalog
                .fromJson("{\"wallets\":[{\"id\":\"x\"}]}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SourceException.class);
    }
}
