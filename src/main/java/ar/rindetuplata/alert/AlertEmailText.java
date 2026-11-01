package ar.rindetuplata.alert;

/**
 * The Spanish pieces every alert email shares (T6c, L32 a): the filter a rule watches and the closing block with the
 * rule number, the link to manage the alerts and the informational disclaimer. The wording of the original
 * {@link AlertEmailComposer} stays in that class; these are the pieces its three siblings reuse.
 */
final class AlertEmailText {

	/** The window of the real return the ranking computes for the alerts (30 days). */
	static final String REAL_RETURN_LABEL = "real a 30 días";

	private AlertEmailText() {
	}

	/** Where a rule looks, as a phrase that follows "la mejor opción": its kind or every option. */
	static String scope(String ruleKind) {
		return switch (ruleKind == null ? "" : ruleKind) {
			case "PLAZO_FIJO" -> "entre los plazos fijos";
			case "WALLET" -> "entre las billeteras";
			case "FUND" -> "entre los fondos";
			case "CRYPTO" -> "entre las opciones cripto";
			default -> "entre todas las opciones";
		};
	}

	/** The block that closes every alert email, preceded by a blank line. */
	static String footer(long ruleId, String manageUrl) {
		return """

				Regla #%d. Podés ver el detalle y administrar tus alertas acá:
				%s

				rindetuplata compara rendimientos con datos públicos y no da asesoramiento financiero.
				""".formatted(ruleId, manageUrl);
	}

}
