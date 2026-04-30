package com.tss.grc.common.model;

import com.tss.grc.security.GrcUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("springSecurityAuditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {
    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof GrcUserDetails)) {
            return Optional.of(1L); // system user
        }
        return Optional.of(((GrcUserDetails) auth.getPrincipal()).getUserId());
    }
}
