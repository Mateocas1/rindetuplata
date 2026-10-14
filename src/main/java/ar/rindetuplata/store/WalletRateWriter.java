package ar.rindetuplata.store;

import ar.rindetuplata.source.wallet.WalletRate;
import ar.rindetuplata.source.wallet.WalletTier;
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
 * Stores a remunerated wallet or account per capture date. {@code rate} is the base TNA, the first unconditional
 * tier; the whole schedule and the labeled conditions are kept as given in {@code details}. Curated rates carry
 * their {@code verifiedAt} date.
 */
@Component
public class WalletRateWriter {


	private final JdbcClient jdbc;
	private final InstrumentStore instruments;

	public WalletRateWriter(JdbcClient jdbc, InstrumentStore instruments) {
		this.jdbc = jdbc;
		this.instruments = instruments;
	}

	/** The natural key of the design notes: {@code walletId}. */
	public static String naturalKey(WalletRate rate) {
		return rate.walletId() == null ? "" : rate.walletId();
	}

	/** The base TNA: the first unconditional tier, or {@code null} when the wallet publishes none. */
	public static BigDecimal baseTna(WalletRate rate) {
		for (WalletTier tier : rate.tiers()) {
			if (tier.tna() != null) {
				return tier.tna();
			}
		}
		return null;
	}

	@Transactional
	public WriteResult write(List<WalletRate> rates, LocalDate captureDate) {
		Map<String, WalletRate> distinct = new LinkedHashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (WalletRate rate : rates) {
			String key = naturalKey(rate);
			if (distinct.putIfAbsent(key, rate) != null) {
				duplicates.add(key);
			}
		}
		for (Map.Entry<String, WalletRate> entry : distinct.entrySet()) {
			WalletRate rate = entry.getValue();
			long instrumentId = instruments.upsert(InstrumentKind.WALLET, rate.source().id(), entry.getKey(),
					rate.name(), null, null, captureDate);
			jdbc.sql(RateSnapshotSql.UPSERT)
					.param("instrumentId", instrumentId)
					.param("captureDate", captureDate)
					.param("dataDate", rate.dataDate())
					.param("basis", "TNA")
					.param("rate", baseTna(rate))
					.param("clientRate", (BigDecimal) null)
					.param("minimumAmount", (BigDecimal) null)
					.param("details", StoreJson.write(Map.of("tiers", rate.tiers(), "conditions", rate.conditions())))
					.param("confidence", rate.confidence().name())
					.param("verifiedAt", rate.verifiedAt())
					.param("fetchedAt", InstantTimes.of(rate.fetchedAt()))
					.param("flags", StoreJson.flags(rate.flags()))
					.param("invalid", rate.isInvalid())
					.update();
		}
		return WriteResult.of(distinct.size(), duplicates);
	}
}
