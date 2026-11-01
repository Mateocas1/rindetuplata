package ar.rindetuplata.api.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One capture day of a bank's non-client TNA series. */
@Schema(description = "A capture day and the bank's non-client TNA (fraction, scale 6) captured that day.")
public record BankSeriesPoint(LocalDate captureDate, BigDecimal nonClientTna) {
}
