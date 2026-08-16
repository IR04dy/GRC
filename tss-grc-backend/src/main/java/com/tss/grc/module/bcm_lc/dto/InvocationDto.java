package com.tss.grc.module.bcm_lc.dto;

import com.tss.grc.module.bcm_lc.entity.Invocation;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvocationDto {
    private String id;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    @NotBlank @Pattern(regexp = "SEV1|SEV2|SEV3|SEV4")
    private String severity;
    @NotBlank private String triggerEventEn;
    @NotBlank private String triggerEventAr;
    @NotBlank private String affectedBiaIds;
    private String affectedSites;
    private String incidentCommander;
    private String bcmCoordinator;
    private String status;
    private LocalDateTime detectedAt;
    private LocalDateTime declaredAt;
    private String declaredBy;
    private LocalDateTime invokedAt;
    private String invokedStrategies;
    private LocalDateTime stabilisedAt;
    private LocalDateTime standDownAt;
    private String standDownAuthoriser;
    private LocalDateTime reviewCompletedAt;
    private BigDecimal actualRtoHours;
    private BigDecimal actualRpoHours;
    private BigDecimal estimatedLossSar;
    private LocalDateTime regulatorNotifiedAt;
    private String regulatorReference;
    private Boolean samaNotified;
    private Boolean ncemaNotified;
    private String pirFindingsEn;
    private String pirFindingsAr;
    private Integer pirCapaCount;

    public static InvocationDto from(Invocation e) {
        return InvocationDto.builder()
            .id(e.getExternalId())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .severity(e.getSeverity())
            .triggerEventEn(e.getTriggerEventEn()).triggerEventAr(e.getTriggerEventAr())
            .affectedBiaIds(e.getAffectedBiaIds()).affectedSites(e.getAffectedSites())
            .incidentCommander(e.getIncidentCommander()).bcmCoordinator(e.getBcmCoordinator())
            .status(e.getStatus())
            .detectedAt(e.getDetectedAt())
            .declaredAt(e.getDeclaredAt()).declaredBy(e.getDeclaredBy())
            .invokedAt(e.getInvokedAt()).invokedStrategies(e.getInvokedStrategies())
            .stabilisedAt(e.getStabilisedAt())
            .standDownAt(e.getStandDownAt()).standDownAuthoriser(e.getStandDownAuthoriser())
            .reviewCompletedAt(e.getReviewCompletedAt())
            .actualRtoHours(e.getActualRtoHours()).actualRpoHours(e.getActualRpoHours())
            .estimatedLossSar(e.getEstimatedLossSar())
            .regulatorNotifiedAt(e.getRegulatorNotifiedAt()).regulatorReference(e.getRegulatorReference())
            .samaNotified(e.getSamaNotified()).ncemaNotified(e.getNcemaNotified())
            .pirFindingsEn(e.getPirFindingsEn()).pirFindingsAr(e.getPirFindingsAr())
            .pirCapaCount(e.getPirCapaCount())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "STANDBY|DECLARED|INVOKED|STABILISED|STAND_DOWN|POST_INCIDENT_REVIEW|CLOSED")
        private String status;
        // F2-224 — declaredBy is gone: the declarer is the authenticated principal, resolved in
        // InvocationService#transition. standDownAuthoriser below is left ALONE and FILED, not
        // blessed — see the STAND_DOWN branch of that method.
        private String invokedStrategies;       // comma-sep RS external ids
        private String standDownAuthoriser;
        private BigDecimal actualRtoHours;
        private BigDecimal actualRpoHours;
        private BigDecimal estimatedLossSar;
        private String regulatorReference;
        private Boolean samaNotified;
        private Boolean ncemaNotified;
        private String pirFindingsEn;
        private String pirFindingsAr;
        private Integer pirCapaCount;
    }
}
