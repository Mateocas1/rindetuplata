package ar.rindetuplata.store;

import ar.rindetuplata.source.crypto.CryptoYield;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores a platform yield per (platform, currency, capture date). The rate basis is APY, as the source gives it. */
@Component
public class CryptoYieldWriter {


	private final JdbcClient jdbc;
	private final InstrumentStore instruments;

	public CryptoYieldWriter(JdbcClient jdbc, InstrumentStore instruments) {
		this.jdbc = jdbc;
		this.instruments = instruments;
	}

	/** The natural key of the design notes: {@code platform|currency}. */
	public static String naturalKey(CryptoYield yield) {
		return text(yield.platform()) + "|" + text(yield.currency());
	}

	@Transactional
	public WriteResult write(List<CryptoYield> yields, LocalDate captureDate) {
		Map<String, CryptoYield> distinct = new LinkedHashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (CryptoYield yield : yields) {
			String key = naturalKey(yield);
			if (distinct.putIfAbsent(key, yield) != null) {
				duplicates.add(key);
			}
		}
		for (Map.Entry<String, CryptoYield> entry : distinct.entrySet()) {
			CryptoYield yield = entry.getValue();
			long instrumentId = instruments.upsert(InstrumentKind.CRYPTO_YIELD, yield.source().id(), entry.getKey(),
					text(yield.platform()) + " " + text(yield.currency()), yield.platform(), null, captureDate);
			jdbc.sql(RateSnapshotSql.UPSERT)
					.param("instrumentId", instrumentId)
					.param("captureDate", captureDate)
					.param("dataDate", yield.date())
					.param("basis", "APY")
					.param("rate", yield.apy())
					.param("clientRate", (BigDecimal) null)
					.param("minimumAmount", (BigDecimal) null)
					.param("details", detailsJson(yield))
					.param("confidence", yield.confidence().name())
					.param("verifiedAt", (LocalDate) null)
					.param("fetchedAt", InstantTimes.of(yield.fetchedAt()))
					.param("flags", StoreJson.flags(yield.flags()))
					.param("invalid", yield.isInvalid())
					.update();
		}
		return WriteResult.of(distinct.size(), duplicates);
	}

	private static String detailsJson(CryptoYield yield) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("platform", yield.platform());
		details.put("currency", yield.currency());
		return StoreJson.write(details);
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}
}
