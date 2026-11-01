package ar.rindetuplata.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The INDEC monthly inflation (BCRA variable 27) a report is measured against: the rate as a fraction, the date the
 * observation is stored under (the last day of the month it measures), how it was obtained and its source id.
 */
public record MonthlyInflation(BigDecimal value, LocalDate observationDate, String confidence, String sourceId) {
}
