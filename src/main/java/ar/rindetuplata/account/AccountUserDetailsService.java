package ar.rindetuplata.account;

import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads an account for Spring Security. T6a logs in through a consumed one-time token, so this service is what
 * gives the context a real {@code UserDetailsService} (no generated default user) and the roles of an email;
 * T6c reuses it for the alert API.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final AccountRepository accounts;

	private final AccountRoles roles;

	public AccountUserDetailsService(AccountRepository accounts, AccountRoles roles) {
		this.accounts = accounts;
		this.roles = roles;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		String email = Emails.normalize(username);
		accounts.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
		// Accounts have no password: an unguessable one per load keeps any future password-based login closed.
		return User.withUsername(email).password("{noop}" + UUID.randomUUID()).authorities(roles.of(email)).build();
	}
}
