package ar.rindetuplata.api.report;

import java.math.BigDecimal;

/** One reportable month of the list: its INDEC inflation and how many instruments were measured and beat it. */
public record ReportMonthView(String month, BigDecimal monthlyInflation, int ranked, int beatInflation) {
}
