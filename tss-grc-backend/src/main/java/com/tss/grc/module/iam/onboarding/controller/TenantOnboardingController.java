package com.tss.grc.module.iam.onboarding.controller;

import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.OnboardTenantRequest;
import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.OnboardTenantResponse;
import com.tss.grc.module.iam.onboarding.service.TenantOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F2-81 — tenant onboarding. The platform could not create an organisation before this.
 *
 * <p>{@code OrganisationController} exposed only {@code GET /accessible} and
 * {@code POST /switch/{id}}, and <b>nothing in the codebase wrote through
 * {@code OrganisationRepository}</b> — re-measured before building this: 20 files reference it, 0
 * call {@code save}. Organisations 1–3 exist because a Python bootstrap inserted them, so onboarding
 * a customer required a DBA with {@code sa} on the production database. Under SAMA outsourcing and
 * NCA ECC change-control expectations, provisioning a tenant by ad-hoc SQL is not an auditable
 * process.
 *
 * <h3>⚠ Why this lives under {@code iam} and not {@code platform}</h3>
 * It was written in {@code module/platform/onboarding} first, and
 * {@code ModuleGraphRatchetTest.kernelInversionsOnlyEverDecrease} rejected it: {@code platform} is
 * kernel-ish, {@code iam} is not, and the onboarding service must reach {@code UserService} to create
 * the tenant's first administrator — a kernel→business inversion, which ADR-002's "kernel boots
 * alone" forbids and the ratchet may only ever see fall. Moving the feature here makes the same
 * dependency run {@code iam}→{@code platform}: business depending on kernel, which is legal under
 * both readings of the boundary rule. The alternative was a port in {@code platform} with an adapter
 * here — the {@code change_adapters} pattern — which is more machinery for a dependency that is
 * simply pointing the wrong way. <b>The ratchet was right and the first placement was wrong.</b>
 *
 * <h3>⚠ Why the guard is a permission and not {@code hasRole('PLATFORM_ADMIN')}</h3>
 * The role name is the correct <i>population</i>, but {@code RoleGuardConversionRatchetTest} counts
 * pure role guards under {@code com.tss.grc.module.**} and lets them only decrease: a role list
 * cannot be re-granted without shipping a jar, and it is invisible to the grant-tightness join. So
 * this is {@code hasPermission('PLATFORM_ADMIN','WRITE')}.
 *
 * <p>Which permission mattered, and it was measured rather than assumed.
 * {@code hasPermission('PLATFORM','WRITE')} was the obvious candidate and is <b>wrong</b>:
 * {@code PLATFORM:WRITE} is granted to {@code ORG_ADMIN}, a <i>tenant</i> administrator, so using it
 * would let any customer's admin create tenants — a privilege escalation dressed as a ratchet fix.
 * {@code PLATFORM_ADMIN} is a separate catalogued module (READ/WRITE/EXPORT) that had <b>zero</b>
 * grants; V359 grants it to the {@code PLATFORM_ADMIN} role, so the guard admits exactly that role
 * and does not lean on {@code ModulePermissionEvaluator}'s code bypass to work.
 */
@RestController
@RequestMapping("/api/v1/iam/tenants")
@RequiredArgsConstructor
@Tag(name = "IAM — Tenant Onboarding")
@SecurityRequirement(name = "bearerAuth")
public class TenantOnboardingController {

    private final TenantOnboardingService onboarding;

    /**
     * Onboard a tenant: organisation, every ratified SKU, and a first administrator — in one
     * transaction.
     *
     * <p>All-or-nothing because F2-04 made the licence half load-bearing: an organisation without
     * licence rows is fail-closed on ~2,138 endpoints, and that arrives as an apparent outage rather
     * than as a missing provisioning step. An organisation without an administrator is a tenant
     * nobody can log into. A half-provisioned tenant is worse than none.
     *
     * <p>Returns 201 with the short code, because since F2-83 that is what a public portal link must
     * carry as {@code ?org=} — the response says so rather than leaving the operator to find out.
     */
    @PostMapping
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','WRITE')")
    @Operation(summary = "Onboard a tenant: organisation + every ratified SKU + first administrator, in one transaction.",
               description = "Host-operator only. Refuses a duplicate short code with 409 naming the "
                           + "field, and writes nothing at all when it refuses (F2-81).")
    public ResponseEntity<OnboardTenantResponse> onboard(@Valid @RequestBody OnboardTenantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(onboarding.onboard(req));
    }
}
