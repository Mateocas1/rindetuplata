package ar.rindetuplata.source.wallet;

import java.math.BigDecimal;

/**
 * A balance band and its TNA (fraction). {@code floor} is the minimum balance that earns it and {@code ceiling}
 * the balance up to which it applies; {@code null} means "no bound". Balance beyond the last ceiling earns nothing
 * unless another tier covers it.
 */
public record WalletTier(BigDecimal floor, BigDecimal ceiling, BigDecimal tna) {
}
