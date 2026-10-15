package ar.rindetuplata.account;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.GrantedAuthority;

/**
 * {@link AccountRoles} whose admin grant a test can revoke after a session exists, which stands for removing an
 * email from {@code rindetuplata.auth.admin-emails} on a running deployment (the property itself is bound once, so
 * a test cannot change it between two requests). Without a revocation it behaves exactly like the real bean.
 */
final class MutableAccountRoles extends AccountRoles {

	private final Set<String> revoked = ConcurrentHashMap.newKeySet();

	MutableAccountRoles(AccountProperties properties) {
		super(properties);
	}

	@Override
	public List<GrantedAuthority> of(String email) {
		List<GrantedAuthority> authorities = super.of(email);
		if (!revoked.contains(Emails.normalize(email))) {
			return authorities;
		}
		return authorities.stream().filter(authority -> !ADMIN.equals(authority.getAuthority())).toList();
	}

	void revokeAdmin(String email) {
		revoked.add(Emails.normalize(email));
	}

	void reset() {
		revoked.clear();
	}
}
