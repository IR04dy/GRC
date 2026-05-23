package com.tss.grc.module.governance.threelines.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Admin mutation — re-map a role to a different LoD band for the current
 * tenant. Triggers F7 (audit log row) + F8 ({@code LOD_ROLE_MAPPING_CHANGED}
 * domain event).
 */
public record RoleMappingUpdateRequest(
        @NotBlank String roleName,
        @NotBlank
        @Pattern(regexp = "^(LINE_1|LINE_2|LINE_3|BOARD)$",
                 message = "lodBand must be one of LINE_1, LINE_2, LINE_3, BOARD")
        String lodBand,
        String titleEn,
        String titleAr,
        String rationaleEn,
        String rationaleAr
) {}
