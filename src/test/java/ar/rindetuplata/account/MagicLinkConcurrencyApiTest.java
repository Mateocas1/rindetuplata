package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;

/**
 * T11a races (S4, S13): ten simultaneous magic-link requests for one email leave at most 3 unexpired tokens and send
 * at most 3 mails, the mail goes out only after the token committed, and two simultaneous first logins of one email
 * both succeed and leave one account. Threads are released together and the assertions read the final rows.
 */
class MagicLinkConcurrencyApiTest extends AccountConcurrencyTest {

	private static final int THREADS = 10;

	@Test
	void tenConcurrentRequestsForOneEmailLeaveThreeTokensAndSendThreeMails() throws Exception {
		String email = "cap" + DOMAIN;
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> postMagicLink(email));
		}

		List<Integer> statuses = concurrently(tasks);

		assertThat(statuses).as("every request is accepted, capped or not").containsOnly(202);
		assertThat(tokenRows(email)).as("at most 3 unexpired tokens").isEqualTo(3);
		assertThat(mailer.sent()).as("exactly the 3 tokens that exist are mailed").hasSize(3);
		assertThat(mailedTokens(email)).doesNotHaveDuplicates();
	}

	@Test
	void concurrentRequestsForDifferentEmailsAreNotSerialisedIntoDroppingMails() throws Exception {
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			String email = "user" + i + DOMAIN;
			tasks.add(() -> postMagicLink(email));
		}

		assertThat(concurrently(tasks)).containsOnly(202);

		assertThat(mailer.sent()).as("each email gets its own mail").hasSize(THREADS);
		for (int i = 0; i < THREADS; i++) {
			assertThat(tokenRows("user" + i + DOMAIN)).isEqualTo(1);
		}
	}

	@Test
	void theMailGoesOutAfterTheTokenTransactionCommitted() throws Exception {
		List<Boolean> insideTransaction = new CopyOnWriteArrayList<>();
		mailer.onSend(message -> insideTransaction.add(TransactionSynchronizationManager.isActualTransactionActive()));

		assertThat(postMagicLink("after" + DOMAIN)).isEqualTo(202);

		assertThat(insideTransaction).as("one mail sent").hasSize(1);
		assertThat(insideTransaction).as("no transaction open while mailing").containsOnly(false);
		assertThat(tokenRows("after" + DOMAIN)).isEqualTo(1);
	}

	@Test
	void twoConcurrentFirstLoginsOfOneEmailBothSucceedAndLeaveOneAccount() throws Exception {
		// One attempt rarely hits the narrow check-then-insert window, so repeat it over several fresh emails.
		for (int round = 0; round < 15; round++) {
			String email = "first" + round + DOMAIN;
			assertThat(postMagicLink(email)).isEqualTo(202);
			assertThat(postMagicLink(email)).isEqualTo(202);
			List<String> tokens = mailedTokens(email);
			assertThat(tokens).hasSize(2);
			List<Callable<Integer>> logins = List.of(() -> login(tokens.get(0)), () -> login(tokens.get(1)));

			assertThat(concurrently(logins)).as("round %d: both logins answer 200", round).containsOnly(200);
			assertThat(accountRows(email)).as("round %d: one account", round).isEqualTo(1);
		}
	}
}
