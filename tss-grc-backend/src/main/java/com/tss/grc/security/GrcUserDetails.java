package com.tss.grc.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Spring Security UserDetails implementation carrying TSS-specific claims.
 *
 * <p>Extends standard UserDetails with {@code userId} and {@code organisationId}
 * so that {@link JwtTokenProvider} can embed tenant context in the JWT and
 * {@link JwtAuthenticationFilter} can restore it on every request without
 * a database lookup.
 */
@Getter
public class GrcUserDetails implements UserDetails {

    private final Long   userId;
    private final Long   organisationId;
    private final String username;
    private final String password;
    private final boolean accountNonLocked;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public GrcUserDetails(Long userId,
                          Long organisationId,
                          String username,
                          String password,
                          boolean enabled,
                          boolean accountNonLocked,
                          Collection<? extends GrantedAuthority> authorities) {
        this.userId          = userId;
        this.organisationId  = organisationId;
        this.username        = username;
        this.password        = password;
        this.enabled         = enabled;
        this.accountNonLocked = accountNonLocked;
        this.authorities     = authorities;
    }

    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()   { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()            { return enabled; }
}
