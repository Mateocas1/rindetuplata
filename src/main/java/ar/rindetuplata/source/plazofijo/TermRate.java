package ar.rindetuplata.source.plazofijo;

import java.math.BigDecimal;

/** A rate band of a bank: term range, optional amount range and the TNA (fraction) for that band. */
public record TermRate(Integer minDays, Integer maxDays, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal tna) {
}
