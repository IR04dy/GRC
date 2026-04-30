package com.tss.grc.module.iam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
    Long id,
    String username,
    String email,
    String fullNameEn,
    String fullNameAr,
    String jobTitleEn,
    Boolean isActive,
    Boolean isLocked,
    Set<String> roles,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {
    /** Alias for isActive — used by the frontend toggle (u.enabled) */
    @JsonProperty("enabled")
    public Boolean enabled() {
        return isActive;
    }
}
