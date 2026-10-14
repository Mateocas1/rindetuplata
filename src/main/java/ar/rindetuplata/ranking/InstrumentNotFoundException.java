package ar.rindetuplata.ranking;

/** No ranked instrument carries this id for the requested date. The API layer maps it to 404. */
public class InstrumentNotFoundException extends RuntimeException {

	public InstrumentNotFoundException(long instrumentId) {
		super("Unknown instrument: " + instrumentId);
	}
}
