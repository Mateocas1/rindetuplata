package ar.rindetuplata.curation;

import java.math.BigDecimal;

/**
 * One balance band of a wallet rate as the admin panel gives it: TNA in percent, floor and ceiling in ARS.
 * {@code floor} absent means no floor and {@code ceiling} absent means no ceiling.
 */
public record TierRequest(BigDecimal floor, BigDecimal ceiling, BigDecimal tnaPercent) {
}
