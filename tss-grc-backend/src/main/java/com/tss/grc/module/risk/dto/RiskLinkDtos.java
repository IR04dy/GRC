package com.tss.grc.module.risk.dto;

import com.tss.grc.module.risk.entity.RiskLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Wire DTOs for typed register linkage (ERM epic E-G). */
public final class RiskLinkDtos {
    private RiskLinkDtos() {}

    public record CreateDto(
        @NotBlank @Size(max = 30)  String linkType,
        @NotBlank @Size(max = 300) String targetRef,
        Long targetId,
        @Size(max = 1000) String descriptionEn
    ) {}

    public record Response(
        Long id, Long riskId, String linkType, String targetRef,
        Long targetId, String descriptionEn, LocalDateTime createdAt
    ) {
        public static Response from(RiskLink l) {
            return new Response(l.getId(), l.getRiskId(), l.getLinkType(), l.getTargetRef(),
                l.getTargetId(), l.getDescriptionEn(), l.getCreatedAt());
        }
    }
}
