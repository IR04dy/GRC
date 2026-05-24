package com.tss.grc.module.platform.help.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.platform.help.entity.HelpTopic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request / response DTOs for the {@code /api/v1/platform/help/*} surface.
 */
public final class HelpDtos {

    private HelpDtos() {}

    // ─── Request shape ────────────────────────────────────────────

    public record HelpTopicRequest(
            @NotBlank @Size(max = 64)  String topicCode,
            @NotBlank @Size(max = 255) String titleEn,
            @NotBlank @Size(max = 255) String titleAr,
            @Size(max = 500)           String summaryEn,
            @Size(max = 500)           String summaryAr,
            @NotBlank                  String bodyEn,
            @NotBlank                  String bodyAr,
            @NotBlank @Size(max = 255) String routePattern,
            @NotBlank @Size(max = 40)  String module,
            @Size(max = 500)           String regulatoryAnchor,
            @Size(max = 500)           String regulatorLinkUrl,
            Long                       relatedKbArticleId,
            @Size(max = 500)           String relatedWorkflowCodes,
            @Size(max = 20)            String severity,
            Integer                    displayOrder
    ) {}

    // ─── Response shape ───────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HelpTopicResponse(
            Long id,
            UUID externalId,
            Long organisationId,
            String topicCode,
            String titleEn,
            String titleAr,
            String summaryEn,
            String summaryAr,
            String bodyEn,
            String bodyAr,
            String routePattern,
            String module,
            String regulatoryAnchor,
            String regulatorLinkUrl,
            Long relatedKbArticleId,
            String relatedWorkflowCodes,
            String severity,
            Integer displayOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static HelpTopicResponse from(HelpTopic h) {
            return new HelpTopicResponse(
                    h.getId(), h.getExternalId(), h.getOrganisationId(),
                    h.getTopicCode(),
                    h.getTitleEn(), h.getTitleAr(),
                    h.getSummaryEn(), h.getSummaryAr(),
                    h.getBodyEn(), h.getBodyAr(),
                    h.getRoutePattern(), h.getModule(),
                    h.getRegulatoryAnchor(), h.getRegulatorLinkUrl(),
                    h.getRelatedKbArticleId(),
                    h.getRelatedWorkflowCodes(),
                    h.getSeverity(), h.getDisplayOrder(),
                    h.getCreatedAt(), h.getUpdatedAt()
            );
        }
    }

    /**
     * Drawer lookup result — a list of matching topics ordered by
     * precedence (org-override first, then exact-match, then
     * wildcard) plus the resolved route the lookup ran against.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HelpLookupResponse(
            String route,
            java.util.List<HelpTopicResponse> topics
    ) {}
}
