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
 *   POST /api/v1/licensing/modules         upsert (admin) — body may name any organisation (F2-45)
 *   POST /api/v1/licensing/modules/:code/disable[?organisationId=]  shortcut
 *   GET  /api/v1/licensing/catalog         every module the platform will accept on a row
 *   GET  /api/v1/licensing/catalog/sellable  the 15 ratified SKUs — what may be newly licensed
 * </pre>
 *
 * <p>There is deliberately no cross-tenant licence <em>read</em>: a {@code PLATFORM_ADMIN} switches
 * into an organisation ({@code POST /api/v1/platform/organisations/switch/{id\}}) and reads
 * {@code GET /modules}. Adding a second role-guarded endpoint to save a round trip would raise the
 * F2-05 role-guard count for a capability that already exists.
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

    /**
     * F2-45. This method used to read {@code body.setOrganisationId(TenantContext.getCurrentTenant())}
     * with the comment "keeps cross-tenant writes from sneaking through". It did not keep anything
     * out — {@code PLATFORM_ADMIN} is the only caller and can switch tenants at will. What it did was
     * <b>answer 200 for the wrong organisation</b>: a body naming org 2 produced a row for org 1 and
     * said nothing about it. Licensing is the platform's one legitimate body-supplied tenant
     * (F2-22b), so the body is now honoured, and {@code resolveLicensingTarget} refuses an
     * organisation that does not exist. Omitting {@code organisationId} still means "the org I am
     * switched into", so no existing caller changes.
     */
    @PostMapping("/modules")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<OrganisationLicense> upsert(@RequestBody OrganisationLicense body) {
        body.setOrganisationId(service.resolveLicensingTarget(body.getOrganisationId()));
        return ResponseEntity.ok(service.upsert(body));
    }

    /**
     * {@code organisationId} is optional and defaults to the caller's tenant, exactly as
     * {@link #upsert} does — deliberately, because <b>a licence you can grant across tenants and
     * cannot revoke across tenants is worse than one you can do neither to</b>. The operator would
     * have to switch tenants to undo their own mistake, which is the state F2-45 describes in the
     * other direction.
     */
    @PostMapping("/modules/{code}/disable")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> disable(@PathVariable String code,
                                        @RequestParam(required = false) Long organisationId) {
        service.disable(service.resolveLicensingTarget(organisationId), code);
        return ResponseEntity.noContent().build();
    }

    /** Every code a licence row may carry — the 15 ratified SKUs plus 12 retired codes rows name. */
    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public Set<String> catalog() { return LicensingService.KNOWN_MODULES; }

    /**
     * The 15 ratified SKUs — what may be <em>newly</em> licensed. Split from {@link #catalog()}
     * because a UI that offers the union offers twelve codes the platform will refuse to create,
     * and a "why did that fail" ticket is the natural consequence of an admin screen listing
     * options that cannot be chosen.
     */
    @GetMapping("/catalog/sellable")
    @PreAuthorize("isAuthenticated()")
    public Set<String> sellableCatalog() { return LicensingService.SELLABLE_MODULES; }
}
