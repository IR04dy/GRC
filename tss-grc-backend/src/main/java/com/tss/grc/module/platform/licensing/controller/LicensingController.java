package com.tss.grc.module.platform.licensing.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST surface for module licensing — F9.
 *
 * <pre>
 *   GET  /api/v1/licensing/modules         current tenant's licenses
 *   GET  /api/v1/licensing/me/enabled      enabled module codes for current user/tenant
 *   POST /api/v1/licensing/modules         upsert (admin)
 *   POST /api/v1/licensing/modules/:code/disable  shortcut
 *   GET  /api/v1/licensing/catalog         every module the platform knows about
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/licensing")
@RequiredArgsConstructor
@Tag(name = "Module Licensing (F9)")
public class LicensingController {

    private final LicensingService service;

    @GetMapping("/modules")
    @PreAuthorize("isAuthenticated()")
    public List<OrganisationLicense> list() { return service.list(); }

    /** What the JWT bakes into the enabled_modules claim. */
    @GetMapping("/me/enabled")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> myEnabled() {
        Long org = TenantContext.getCurrentTenant();
        Set<String> enabled = service.enabledModulesFor(org);
        return Map.of("organisationId", org, "enabled_modules", enabled);
    }

    @PostMapping("/modules")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<OrganisationLicense> upsert(@RequestBody OrganisationLicense body) {
        // Force the current tenant onto the body — keeps cross-tenant writes from sneaking through.
        body.setOrganisationId(TenantContext.getCurrentTenant());
        return ResponseEntity.ok(service.upsert(body));
    }

    @PostMapping("/modules/{code}/disable")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> disable(@PathVariable String code) {
        service.disable(TenantContext.getCurrentTenant(), code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public Set<String> catalog() { return LicensingService.KNOWN_MODULES; }
}
