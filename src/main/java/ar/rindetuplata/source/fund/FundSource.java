package ar.rindetuplata.source.fund;

import java.util.List;

/** Money-market fund quotes: the latest business day and the one before it. */
public interface FundSource {

    List<FundQuote> fetchLatest();

    List<FundQuote> fetchPrevious();
}
