package ar.rindetuplata.store;

import ar.rindetuplata.source.plazofijo.OfficialPlazoFijoRate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one row of the BCRA transparency regime per (instrument, capture date). Re-running a capture date replaces
 * the values of the same key, so a day leaves exactly one row per instrument. Rows flagged INVALID are stored with
 * {@code invalid = true}. A response that repeats a natural key keeps the first record and reports the rest.
 */
@Component
public class OfficialPlazoFijoWriter {

	public static final String SOURCE_ID = "bcra-transparencia-plazos-fijos";


	private final JdbcClient jdbc;
	private final InstrumentStore instruments;

	public OfficialPlazoFijoWriter(JdbcClient jdbc, InstrumentStore instruments) {
		this.jdbc = jdbc;
		this.instruments = instruments;
	}

	/** The natural key of the design notes: {@code entityCode|productName|channel|termCode|minimumAmount}. */
	public static String naturalKey(OfficialPlazoFijoRate rate) {
		return String.join("|",
				text(rate.entityCode()),
				text(rate.productName()),
				text(rate.channel()),
				text(rate.termCode()),
				text(rate.minimumAmount()));
	}

	@Transactional
	public WriteResult write(List<OfficialPlazoFijoRate> rates, LocalDate captureDate) {
		Map<String, OfficialPlazoFijoRate> distinct = new LinkedHashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (OfficialPlazoFijoRate rate : rates) {
			String key = naturalKey(rate);
			if (distinct.putIfAbsent(key, rate) != null) {
				duplicates.add(key);
			}
		}
		for (Map.Entry<String, OfficialPlazoFijoRate> entry : distinct.entrySet()) {
			OfficialPlazoFijoRate rate = entry.getValue();
			long instrumentId = instruments.upsert(InstrumentKind.PLAZO_FIJO_OFFICIAL, rate.source().id(), entry.getKey(),
					rate.productName(), rate.entityName(),
					rate.segment() == null ? null : rate.segment().name(), captureDate);
			jdbc.sql(RateSnapshotSql.UPSERT)
					.param("instrumentId", instrumentId)
					.param("captureDate", captureDate)
					.param("dataDate", rate.dataDate())
					.param("basis", "TEA")
					.param("rate", rate.tea())
					.param("clientRate", (java.math.BigDecimal) null)
					.param("minimumAmount", rate.minimumAmount())
					.param("details", detailsJson(rate))
					.param("confidence", rate.confidence().name())
					.param("verifiedAt", (LocalDate) null)
					.param("fetchedAt", InstantTimes.of(rate.fetchedAt()))
					.param("flags", StoreJson.flags(rate.flags()))
					.param("invalid", rate.isInvalid())
					.update();
		}
		return WriteResult.of(distinct.size(), duplicates);
	}

	private static String detailsJson(OfficialPlazoFijoRate rate) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("termCode", rate.termCode());
		details.put("channel", rate.channel());
		details.put("productName", rate.productName());
		details.put("segment", rate.segment() == null ? null : rate.segment().name());
		return StoreJson.write(details);
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}
}
