package ar.rindetuplata.store;

/** The upsert shared by the writers that store one rate row per (instrument, capture date). */
final class RateSnapshotSql {

	static final String UPSERT = """
			insert into rate_snapshot (instrument_id, capture_date, data_date, basis, rate, client_rate, minimum_amount,
			                           details, confidence, verified_at, fetched_at, flags, invalid)
			values (:instrumentId, :captureDate, :dataDate, :basis, :rate, :clientRate, :minimumAmount,
			        cast(:details as jsonb), :confidence, :verifiedAt, :fetchedAt, cast(:flags as jsonb), :invalid)
			on conflict (instrument_id, capture_date) do update
			set data_date = excluded.data_date,
			    basis = excluded.basis,
			    rate = excluded.rate,
			    client_rate = excluded.client_rate,
			    minimum_amount = excluded.minimum_amount,
			    details = excluded.details,
			    confidence = excluded.confidence,
			    verified_at = excluded.verified_at,
			    fetched_at = excluded.fetched_at,
			    flags = excluded.flags,
			    invalid = excluded.invalid
			""";

	private RateSnapshotSql() {
	}
}
