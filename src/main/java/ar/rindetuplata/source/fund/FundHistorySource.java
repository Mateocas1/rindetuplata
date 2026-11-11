package ar.rindetuplata.source.fund;

import java.time.LocalDate;
import java.util.List;

/** Money-market fund quotes of one past day: empty when the source has none for that date. */
public interface FundHistorySource {

    List<FundQuote> fetchDay(LocalDate day);
}
