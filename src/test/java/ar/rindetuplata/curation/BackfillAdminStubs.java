package ar.rindetuplata.curation;

import ar.rindetuplata.ingest.BackfillPause;
import ar.rindetuplata.source.dollar.DollarHistorySource;
import ar.rindetuplata.source.fund.FundHistorySource;
import ar.rindetuplata.source.macro.MacroHistorySource;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * History ports that answer nothing and never wait, so the admin backfill endpoint can be driven without touching the
 * network or the data the other curation tests read. The fund port can be held on a latch to keep a run "running".
 */
@TestConfiguration(proxyBeanMethods = false)
class BackfillAdminStubs {

	/** The days the fund port was asked for, in order. */
	static final List<LocalDate> FUND_DAYS = new CopyOnWriteArrayList<>();

	/** When set, the fund port blocks on it (up to 30 s) before answering. */
	static volatile CountDownLatch hold;

	@Bean
	@Primary
	FundHistorySource emptyFundHistory() {
		return day -> {
			FUND_DAYS.add(day);
			CountDownLatch latch = hold;
			if (latch != null) {
				try {
					latch.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return List.of();
		};
	}

	@Bean
	@Primary
	DollarHistorySource emptyDollarHistory() {
		return List::of;
	}

	@Bean
	@Primary
	MacroHistorySource emptyMacroHistory() {
		return (variable, from, to) -> List.of();
	}

	@Bean
	@Primary
	BackfillPause noPause() {
		return delay -> {
		};
	}
}
