package com.tss.grc.module.iam.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** RBAC Access-Governance P0 (plan §6.1/§8) — request/response payloads. */
public final class GovernanceDtos {

    private GovernanceDtos() {}

    /** Role list/detail payload — deliberately WITHOUT the permissions collection
     *  (serializing the entity lazy-inits Role.permissions outside the session → 500). */
    public record RoleSummary(Long id, String roleName, String displayNameEn, String displayNameAr,
                              boolean systemRole, boolean active, Long organisationId) {}

    public record RoleUpsertRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z][A-Z0-9_]*",
            message = "roleName must be UPPER_SNAKE_CASE") String roleName,
        @NotBlank @Size(max = 200) String displayNameEn,
        // bilingual is mandatory platform-wide (iam.roles.display_name_ar is NOT NULL)
        @NotBlank @Size(max = 200) String displayNameAr,
        Boolean isActive
    ) {}

    public record GrantPair(
        @NotBlank @Size(max = 50) String module,
        @NotBlank @Size(max = 50) String action
    ) {}

    public record MatrixUpdateRequest(@NotNull List<GrantPair> grants) {}

    /** One catalogue cell of the role × module × action grid. */
    public record MatrixCell(String module, String action, String permissionName, boolean granted) {}

    public record RoleMatrixResponse(Long roleId, String roleName, boolean systemRole,
                                     List<MatrixCell> cells) {}

    public record CreateAccessRequest(
        @NotNull Long beneficiaryUserId,
        @NotNull Long roleId,
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validTo,
        @NotBlank @Size(max = 1000) String justification
    ) {}

    public record RejectAccessRequest(@NotBlank @Size(max = 500) String reason) {}

    public record RevokeAssignmentRequest(@NotNull Long userId, @NotNull Long roleId) {}

    /** One resolved grant of a user, with the role(s) that deliver it. */
    public record EffectiveGrant(String module, String action, List<String> viaRoles) {}

    public record EffectiveAccessResponse(Long userId, String username, boolean platformAdmin,
                                          List<String> roles, List<EffectiveGrant> grants) {}

    // ── P2: SoD + recertification (plan §6.4/§6.5) ───────────────────────

    /** Optional approval body — only needed to override a preventive SoD block. */
    public record ApproveAccessRequest(Boolean override, @Size(max = 1000) String overrideJustification) {}

    public record SodDispositionRequest(
        @NotBlank @Pattern(regexp = "ACCEPTED_RISK|REMEDIATED") String status,
        @NotBlank @Size(max = 1000) String note,
        java.time.LocalDate acceptedUntil
    ) {}

    public record CreateCampaignRequest(
        @NotBlank @Size(max = 300) String titleEn,
        @Size(max = 300) String titleAr,
        @NotBlank @Pattern(regexp = "PRIVILEGED|MODULE|ALL") String scope,
        @Size(max = 40) String scopeModule,
        @NotNull java.time.LocalDate windowStart,
        @NotNull java.time.LocalDate windowEnd
    ) {}

    public record ReviewDecisionRequest(
        @NotBlank @Pattern(regexp = "CERTIFY|REVOKE|MODIFY") String decision,
        @Size(max = 500) String note
    ) {}

    /** Optional closure body — exceptionNote only needed when items are still pending. */
    public record CloseCampaignRequest(@Size(max = 1000) String exceptionNote) {}
}
