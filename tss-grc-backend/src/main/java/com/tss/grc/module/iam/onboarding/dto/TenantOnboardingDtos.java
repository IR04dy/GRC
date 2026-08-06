package com.tss.grc.module.iam.onboarding.dto;

import com.tss.grc.common.validation.StrongPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * F2-81 — the request and answer for onboarding a tenant.
 *
 * <p>Before this the platform could not create an organisation at all: {@code OrganisationController}
 * exposed only {@code GET /accessible} and {@code POST /switch/{id}}, and <b>nothing anywhere called
 * {@code save} on {@code OrganisationRepository}</b> — organisations 1–3 exist because a Python
 * bootstrap inserted them. Onboarding a customer meant a DBA with {@code sa} on the production
 * database, which under SAMA outsourcing and NCA ECC change-control expectations is not an auditable
 * process.
 */
public final class TenantOnboardingDtos {

    private TenantOnboardingDtos() {}

    /**
     * @param nameAr required, not optional. House rule 7 — user-visible records carry {@code *_en}
     *               <b>and</b> {@code *_ar}, and a tenant's own name is the most visible record it
     *               has. Making it optional here would guarantee an Arabic-blank tenant, since the
     *               only caller is a host operator in a hurry.
     * @param shortCode the tenant's public, non-enumerable identifier. F2-83 made this load-bearing:
     *               it is what a public portal link carries as {@code ?org=} to say which tenant a
     *               whistleblower report or consumer complaint belongs to. Constrained to the shape
     *               the existing tenants use, upper-cased by the service, and unique
     *               ({@code UQ_org_short_code}).
     */
    public record OnboardTenantRequest(
        @NotBlank @Size(max = 300) String nameEn,
        @NotBlank @Size(max = 300) String nameAr,
        @NotBlank @Size(min = 2, max = 20)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,19}",
                 message = "shortCode must be alphanumeric with - or _, starting with a letter or digit")
        String shortCode,
        @Size(max = 3) String countryCode,
        @Size(max = 20) String subscriptionTier,
        @Size(max = 100) String industrySector,
        @NotNull @Valid FirstAdministrator administrator
    ) {}

    /**
     * The tenant's first administrator.
     *
     * <p>Created in the <b>new</b> organisation, not the caller's — see
     * {@code UserService#createIn}. Without an administrator the tenant would exist and nobody could
     * log into it, which is a half-provisioned tenant, and F2-81's recommendation is explicit that a
     * half-provisioned tenant is worse than none. So this is {@code @NotNull}: there is no onboarding
     * without one, and the whole thing is one transaction.
     */
    public record FirstAdministrator(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 100) @StrongPassword String password,
        @NotBlank @Size(max = 200) String fullNameEn,
        @Size(max = 200) String fullNameAr
    ) {}

    /**
     * @param licensedSkus every ratified SKU, derived from {@code SkuRegistry} rather than listed
     *                     here. {@code licence_coverage.py} warns that the ratified set already has
     *                     three transcriptions the suite keeps in step; a fourth in this DTO would be
     *                     the drift that produces the next F2-01.
     */
    public record OnboardTenantResponse(
        Long organisationId,
        String shortCode,
        String nameEn,
        String nameAr,
        Long administratorUserId,
        String administratorUsername,
        List<String> licensedSkus,
        int licenceRowsCreated,
        String portalLinkHint
    ) {}
}
