package ar.rindetuplata.account;

import java.util.Locale;

/**
 * Email handling for the account: every email is normalized (trim + lower case) before it is stored, compared or
 * used as a one-time-token username, so {@code Saver@Example.com} and {@code saver@example.com} are one account.
 */
public final class Emails {

	private static final int MAX_LENGTH = 254;

	private Emails() {
	}

	/** Trim and lower case, the form stored and compared everywhere. */
	public static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * A deliberately small syntax check: one {@code @}, a non-empty local part, a domain with an inner dot and no
	 * whitespace. It rejects what the API must answer 400 for without pretending to be RFC 5322.
	 */
	public static boolean isValid(String email) {
		if (email == null || email.isEmpty() || email.length() > MAX_LENGTH) {
			return false;
		}
		if (email.chars().anyMatch(Character::isWhitespace)) {
			return false;
		}
		int at = email.indexOf('@');
		if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1) {
			return false;
		}
		String domain = email.substring(at + 1);
		int dot = domain.indexOf('.');
		return dot > 0 && dot < domain.length() - 1;
	}
}
