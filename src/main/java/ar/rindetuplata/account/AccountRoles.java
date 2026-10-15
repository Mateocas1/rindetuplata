package ar.rindetuplata.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * The authorities of an email: every account is {@code ROLE_USER}; an email in
 * {@code rindetuplata.auth.admin-emails} (compared normalized) is also {@code ROLE_ADMIN}, which opens
 * {@code /api/v1/admin/**} (T6a, S12).
 */
@Component
public class AccountRoles {

	public static final String USER = "ROLE_USER";
	public static final String ADMIN = "ROLE_ADMIN";

	private final Set<String> adminEmails;

	public AccountRoles(AccountProperties properties) {
		this.adminEmails = properties.normalizedAdminEmails();
	}

	public List<GrantedAuthority> of(String email) {
		List<GrantedAuthority> authorities = new ArrayList<>(2);
		authorities.add(new SimpleGrantedAuthority(USER));
		if (adminEmails.contains(Emails.normalize(email))) {
			authorities.add(new SimpleGrantedAuthority(ADMIN));
		}
		return List.copyOf(authorities);
	}
}
