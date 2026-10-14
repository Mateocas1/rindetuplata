package ar.rindetuplata.account;

/** The body of {@code POST /api/v1/auth/magic-link}: always the same, whether or not the email is known (S13). */
public record MagicLinkResponse(String status) {

	public static final MagicLinkResponse ACCEPTED = new MagicLinkResponse("accepted");
}
