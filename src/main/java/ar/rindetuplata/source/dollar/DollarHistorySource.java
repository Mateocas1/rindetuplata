package ar.rindetuplata.source.dollar;

import java.util.List;

/** The dated MEP series of a source that keeps history, read once per run. */
public interface DollarHistorySource {

    List<DatedDollarQuote> fetchSeries();
}
