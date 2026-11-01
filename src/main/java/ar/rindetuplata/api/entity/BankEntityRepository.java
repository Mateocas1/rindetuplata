package ar.rindetuplata.api.entity;

import ar.rindetuplata.entity.BankStanding.Observation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read queries behind the bank entity endpoints, straight over the T4 tables with {@code JdbcClient}. A bank is a
 * {@code PLAZO_FIJO_BANK} instrument; only valid (not INVALID) snapshots at or before the given day are read.
 */
@Component
public class BankEntityRepository {

	private final JdbcClient jdbc;

	public BankEntityRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Identity of a bank. */
	public record BankRow(long id, String name, String sourceId) {
	}

	/** A bank's latest valid snapshot. */
	public record LatestRow(long instrumentId, LocalDate captureDate, LocalDate dataDate, BigDecimal nonClientTna,
			BigDecimal clientTna) {
	}

	/** Every bank, in instrument id order: the order slug collisions are resolved in. */
	public List<BankRow> banks() {
		return jdbc.sql("""
				select id, display_name, source_id from instrument where kind = 'PLAZO_FIJO_BANK' order by id
				""")
				.query((rs, rowNum) -> new BankRow(rs.getLong("id"), rs.getString("display_name"),
						rs.getString("source_id")))
				.list();
	}

	/** The latest valid snapshot at or before {@code today} of every bank that has one. */
	public List<LatestRow> latest(LocalDate today) {
		return jdbc.sql("""
				select distinct on (s.instrument_id) s.instrument_id, s.capture_date, s.data_date, s.rate, s.client_rate
				from rate_snapshot s
				join instrument i on i.id = s.instrument_id
				where i.kind = 'PLAZO_FIJO_BANK' and s.invalid = false and s.capture_date <= :today
				order by s.instrument_id, s.capture_date desc
				""")
				.param("today", today)
				.query((rs, rowNum) -> new LatestRow(rs.getLong("instrument_id"),
						rs.getObject("capture_date", LocalDate.class), rs.getObject("data_date", LocalDate.class),
						rs.getObject("rate", BigDecimal.class), rs.getObject("client_rate", BigDecimal.class)))
				.list();
	}

	/** The valid non-client rates of every bank captured in the window, oldest first. */
	public List<Observation> observations(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				select s.instrument_id, s.capture_date, s.rate
				from rate_snapshot s
				join instrument i on i.id = s.instrument_id
				where i.kind = 'PLAZO_FIJO_BANK' and s.invalid = false and s.rate is not null
				  and s.capture_date between :from and :to
				order by s.capture_date, s.instrument_id
				""")
				.param("from", from)
				.param("to", to)
				.query((rs, rowNum) -> new Observation(rs.getLong("instrument_id"),
						rs.getObject("capture_date", LocalDate.class), rs.getObject("rate", BigDecimal.class)))
				.list();
	}
}
