package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T6a magic-link request rules (S13): {@code POST /api/v1/auth/magic-link} answers 202 with the same body whether
 * or not the email is known, rejects an invalid email with a 400 {@code ProblemDetail}, mails the configured link
 * with a usable token, and stops sending after 3 unexpired tokens for the same email.
 */
class MagicLinkRequestApiTest extends AccountIntegrationTest {

	@Test
	void aMagicLinkRequestAnswersTheSameBodyForKnownAndUnknownEmails() throws Exception {
		loginNewSession(USER_EMAIL);
		mailer.clear();

		String known = postMagicLink(USER_EMAIL).andExpect(status().isAccepted()).andReturn()
				.getResponse().getContentAsString();
		String unknown = postMagicLink("nobody@example.com").andExpect(status().isAccepted()).andReturn()
				.getResponse().getContentAsString();

		assertThat(known).as("no account enumeration through the body").isEqualTo(unknown);
	}

	@Test
	void anInvalidEmailIsABadRequestNamingTheParameter() throws Exception {
		expectProblem(postMagicLink("not-an-email"), 400, "Bad Request");
		postMagicLink("not-an-email").andExpect(jsonPath("$.parameter").value("email"));
	}

	@Test
	void theMailCarriesTheConfiguredLinkAndAUsableToken() throws Exception {
		postMagicLink("fresh@example.com").andExpect(status().isAccepted());

		MailMessage message = mailer.last();
		assertThat(message).as("exactly one magic-link email").isNotNull();
		assertThat(message.to()).isEqualTo("fresh@example.com");
		assertThat(message.body()).contains(MAGIC_LINK_URL + "?token=");
		assertThat(mailer.sent()).hasSize(1);

		login(lastMailedToken(), null).andExpect(status().isOk());
	}

	@Test
	void aFourthUnexpiredTokenForTheSameEmailSendsNothing() throws Exception {
		for (int i = 0; i < 3; i++) {
			postMagicLink("cap@example.com").andExpect(status().isAccepted());
		}
		assertThat(mailer.sent()).as("the first three requests send").hasSize(3);

		mailer.clear();
		MvcResult fourth = postMagicLink("cap@example.com").andExpect(status().isAccepted()).andReturn();

		assertThat(fourth.getResponse().getContentAsString()).as("the answer body never changes").isNotEmpty();
		assertThat(mailer.sent()).as("the fourth request sends nothing").isEmpty();
	}
}
