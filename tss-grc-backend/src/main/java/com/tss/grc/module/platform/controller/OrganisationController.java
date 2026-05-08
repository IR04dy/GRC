package com.tss.grc.module.platform.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-tenant org switcher (P1).
 *
 * - GET  /api/v1/platform/organisations/accessible
 *     Lists organisations the current user can switch to. PLATFORM_ADMIN sees all
 *     active organisations; everyone else sees only their own.
 *
 * - POST /api/v1/platform/organisations/switch/{id}
 *     Re-issues an access token scoped to the requested organisation. Restricted
 *     to PLATFORM_ADMIN — non-admin users can only operate in their home org.
 */
@RestController
@RequestMapping("/api/v1/platform/organisations")
@RequiredArgsConstructor
@Tag(name = "Platform — Organisations & Tenant Switcher")
@SecurityRequirement(name = "bearerAuth")
public class OrganisationController {

    private final OrganisationRepository orgRepo;
    private final JwtTokenProvider tokenProvider;

    @GetMapping("/accessible")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List organisations the current user can switch into")
    public List<Map<String, Object>> accessible(Authentication auth) {
        Long currentOrg = TenantContext.getCurrentTenant();
        boolean isAdmin = isPlatformAdmin(auth);

        if (isAdmin) {
            return orgRepo.findAll().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                .map(o -> toView(o, o.getId().equals(currentOrg)))
                .toList();
        }
        // Non-admin: only their home org
        return orgRepo.findById(currentOrg)
            .map(o -> List.of(toView(o, true)))
            .orElseGet(List::of);
    }

    @PostMapping("/switch/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Re-issue an access token scoped to the requested org. Platform-admin only.")
    public ResponseEntity<?> switchOrg(@PathVariable Long id, Authentication auth) {
        // @PreAuthorize already enforces; defensive belt-and-braces in case of misconfiguration:
        if (!isPlatformAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of("message", "Only platform admins can switch tenants"));
        }
        Organisation target = orgRepo.findById(id).orElse(null);
        if (target == null || !Boolean.TRUE.equals(target.getIsActive())) {
            return ResponseEntity.status(404).body(Map.of("message", "Organisation not found or inactive"));
        }
        Long userId = Long.parseLong(auth.getName());
        String accessToken = tokenProvider.generateAccessTokenFromClaims(userId, id);
        Map<String, Object> body = new HashMap<>();
        body.put("accessToken",        accessToken);
        body.put("organisationId",     id);
        body.put("organisationNameEn", target.getNameEn());
        body.put("organisationNameAr", target.getNameAr());
        body.put("shortCode",          target.getShortCode());
        return ResponseEntity.ok(body);
    }

    private static boolean isPlatformAdmin(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority g : auth.getAuthorities()) {
            String a = g.getAuthority();
            if ("ROLE_PLATFORM_ADMIN".equals(a) || "PLATFORM_ADMIN".equals(a)) return true;
        }
        return false;
    }

    private static Map<String, Object> toView(Organisation o, boolean current) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",               o.getId());
        m.put("nameEn",           o.getNameEn());
        m.put("nameAr",           o.getNameAr());
        m.put("shortCode",        o.getShortCode());
        m.put("industrySector",   o.getIndustrySector());
        m.put("countryCode",      o.getCountryCode());
        m.put("subscriptionTier", o.getSubscriptionTier());
        m.put("primaryColor",     o.getPrimaryColor());
        m.put("logoUrl",          o.getLogoUrl());
        m.put("isCurrent",        current);
        return m;
    }
}
