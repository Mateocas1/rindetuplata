package ar.rindetuplata.store;

import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.plazofijo.TermRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a bank's plazo fijo rates: {@code rate} is the non-client TNA (the S19 default) and {@code client_rate}
 * the client one. The term bands are kept as given in {@code details}. One row per (bank, capture date).
 */
@Component
public class BankPlazoFijoWriter {


	private final JdbcClient jdbc;
	private final InstrumentStore instruments;

	public BankPlazoFijoWriter(JdbcClient jdbc, InstrumentStore instruments) {
		this.jdbc = jdbc;
		this.instruments = instruments;
	}

	/** The natural key of the design notes: the bank name. */
	public static String naturalKey(BankPlazoFijoRate rate) {
		return rate.bankName() == null ? "" : rate.bankName();
	}

	@Transactional
	public WriteResult write(List<BankPlazoFijoRate> rates, LocalDate captureDate) {
		Map<String, BankPlazoFijoRate> distinct = new LinkedHashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (BankPlazoFijoRate rate : rates) {
			String key = naturalKey(rate);
			if (distinct.putIfAbsent(key, rate) != null) {
				duplicates.add(key);
			}
		}
		for (Map.Entry<String, BankPlazoFijoRate> entry : distinct.entrySet()) {
			BankPlazoFijoRate rate = entry.getValue();
			long instrumentId = instruments.upsert(InstrumentKind.PLAZO_FIJO_BANK, rate.source().id(), entry.getKey(),
					rate.bankName(), rate.bankName(), null, captureDate);
			jdbc.sql(RateSnapshotSql.UPSERT)
					.param("instrumentId", instrumentId)
					.param("captureDate", captureDate)
					.param("dataDate", rate.dataDate())
					.param("basis", "TNA")
					.param("rate", rate.nonClientTna())
					.param("clientRate", rate.clientTna())
					.param("minimumAmount", (BigDecimal) null)
					.param("details", StoreJson.write(Map.of("terms", terms(rate.terms()))))
					.param("confidence", rate.confidence().name())
					.param("verifiedAt", (LocalDate) null)
					.param("fetchedAt", InstantTimes.of(rate.fetchedAt()))
					.param("flags", StoreJson.flags(rate.flags()))
					.param("invalid", rate.isInvalid())
					.update();
		}
		return WriteResult.of(distinct.size(), duplicates);
	}

	private static List<Map<String, Object>> terms(List<TermRate> terms) {
		List<Map<String, Object>> encoded = new ArrayList<>(terms.size());
		for (TermRate term : terms) {
			Map<String, Object> node = new LinkedHashMap<>();
			node.put("minDays", term.minDays());
			node.put("maxDays", term.maxDays());
			node.put("minAmount", term.minAmount());
			node.put("maxAmount", term.maxAmount());
			node.put("tna", term.tna());
			encoded.add(node);
		}
		return encoded;
	}
}
