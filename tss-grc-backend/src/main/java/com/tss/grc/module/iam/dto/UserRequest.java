package com.tss.grc.module.iam.dto;

import com.tss.grc.common.validation.StrongPassword;
import jakarta.validation.constraints.*;
import java.util.Set;

public record UserRequest(
    @NotBlank @Size(min = 3, max = 100) String username,
    @NotBlank @Email @Size(max = 200) String email,
    // SEC-AUTH-002: strong-password policy (>=12 chars, >=3 character classes).
    // Null/blank passes so an admin update that omits the password keeps the
    // existing one; the service still requires a password on create.
    @Size(max = 100) @StrongPassword String password,
    @NotBlank @Size(max = 200) String fullNameEn,
    @Size(max = 200) String fullNameAr,
    @Size(max = 200) String jobTitleEn,
    /** Legacy field name — kept for compatibility. Use {@code enabled} instead. */
    Boolean isActive,
    /** Preferred field name for active status (used by UI). Alias for isActive. */
    Boolean enabled,
    /** Role names (e.g. PLATFORM_ADMIN) — preferred over roleIds */
    Set<String> roles,
    /** Role IDs — legacy field, use roles (names) instead */
    Set<Long> roleIds
) {
    /** Returns the effective active flag: enabled takes precedence over isActive */
    public Boolean effectiveActive() {
        if (enabled != null) return enabled;
        return isActive;
    }
}
