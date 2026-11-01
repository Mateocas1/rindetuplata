package ar.rindetuplata.source.dollar;

import java.time.LocalDate;

/** A MEP quote together with the calendar day the series gives it. */
public record DatedDollarQuote(LocalDate date, DollarQuote quote) {
}
