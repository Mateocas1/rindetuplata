package ar.rindetuplata.account;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/** A successfully authenticated account: the normalized email and the authorities of its session. */
public record AuthenticatedAccount(String email, List<GrantedAuthority> authorities) {
}
