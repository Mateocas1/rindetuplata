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
import java.util.List;
import org.junit.jupiter.api.Test;

class FciBackedWalletCatalogTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final List<FundQuote> quotes =
            ArgentinaDatosFundAdapter.parse(Fixtures.bytes("argentinadatos/fci-mercado-dinero-ultimo.json"), FETCHED_AT);

    @Test
    void classpathMappingLinksMercadoPagoToMercadoFondoClaseA() {
        FciBackedWalletCatalog catalog = FciBackedWalletCatalog.fromClasspath();

        assertThat(catalog.mappings()).extracting(FciBackedWalletMapping::walletId).containsExactly("mercado-pago");
        assertThat(catalog.mappings().get(0).name()).isEqualTo("Mercado Pago");
        assertThat(catalog.mappings().get(0).fundClass()).isEqualTo("Mercado Fondo - Clase A");
    }

    @Test
    void everyClasspathMappingPointsAtAClassConfirmedInTheRecordedFundResponse() {
        List<FciBackedWallet> wallets = FciBackedWalletCatalog.fromClasspath().resolve(quotes);

        assertThat(wallets).isNotEmpty().allSatisfy(w -> assertThat(w.quote()).isNotNull());
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
