package ar.rindetuplata.account;

import org.springframework.stereotype.Component;

/**
 * Composes the magic-link email: the configured frontend link with the token appended, in Spanish and with the
 * informational wording the product requires (S8 L-G). Plain text, no HTML.
 */
@Component
public class MagicLinkMailComposer {

	static final String SUBJECT = "Tu enlace para ingresar a rindetuplata";

	private final AccountProperties properties;

	public MagicLinkMailComposer(AccountProperties properties) {
		this.properties = properties;
	}

	public MailMessage compose(String email, String token) {
		String link = properties.magicLinkUrl() + "?token=" + token;
		String body = """
				Hola,

				Pediste ingresar a rindetuplata. Abrí este enlace para entrar:

				%s

				El enlace vence en %d minutos y sirve una sola vez.
				Si no pediste este correo, ignoralo: no hace falta que hagas nada.

				rindetuplata compara rendimientos con datos públicos y no da asesoramiento financiero.
				""".formatted(link, properties.tokenTtl().toMinutes());
		return new MailMessage(email, SUBJECT, body);
	}
}
