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
        @Size(max = 200) String displayNameAr,
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

    /** One resolved grant of a user, with the role(s) that deliver it. */
    public record EffectiveGrant(String module, String action, List<String> viaRoles) {}

    public record EffectiveAccessResponse(Long userId, String username, boolean platformAdmin,
                                          List<String> roles, List<EffectiveGrant> grants) {}
}
