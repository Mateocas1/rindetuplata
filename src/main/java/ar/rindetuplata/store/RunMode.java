package ar.rindetuplata.store;

/** How a source produced its rows: fetched live, or replayed from the T9 raw archive. */
public enum RunMode {
	LIVE,
	REPLAY
}
