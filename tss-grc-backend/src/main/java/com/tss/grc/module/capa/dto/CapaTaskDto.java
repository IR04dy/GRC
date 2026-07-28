package com.tss.grc.module.capa.dto;

import com.tss.grc.module.capa.entity.CapaTask;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CapaTaskDto {
    private String id;
    /** Internal Long PK — used by frontend audit-log + automation lookups
     *  (EntityActivityTimeline / EntityAutomationRuns key off this).
     *  Mirrors the FindingDto pattern from PR #45. */
    private Long numericId;
    @NotBlank private String originType;
    @NotBlank private String originId;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    private String descriptionEn;
    private String descriptionAr;
    @NotBlank @Pattern(regexp = "CORRECTIVE|PREVENTIVE")
    private String capaType;
    private String severity;
    @NotBlank private String owner;
    @NotNull private LocalDate targetDate;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedForVerificationAt;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
    private LocalDateTime closedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReasonEn;
    private String progressNotesEn;
    private String progressNotesAr;
    private String evidenceRefs;

    public static CapaTaskDto from(CapaTask e) {
        return CapaTaskDto.builder()
            .id(e.getExternalId()).numericId(e.getId())
            .originType(e.getOriginType()).originId(e.getOriginId())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .descriptionEn(e.getDescriptionEn()).descriptionAr(e.getDescriptionAr())
            .capaType(e.getCapaType()).severity(e.getSeverity())
            .owner(e.getOwner()).targetDate(e.getTargetDate())
            .status(e.getStatus())
            .startedAt(e.getStartedAt())
            .submittedForVerificationAt(e.getSubmittedForVerificationAt())
            .verifiedAt(e.getVerifiedAt()).verifiedBy(e.getVerifiedBy())
            .closedAt(e.getClosedAt())
            .cancelledAt(e.getCancelledAt()).cancellationReasonEn(e.getCancellationReasonEn())
            .progressNotesEn(e.getProgressNotesEn()).progressNotesAr(e.getProgressNotesAr())
            .evidenceRefs(e.getEvidenceRefs())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|AWAITING_VERIFICATION|CLOSED|CANCELLED")
        private String status;
        // F2-22b(vi): verifiedBy removed — it is stamped from the authenticated principal.
        // Leaving it declared would advertise a field the service ignores.
        private String cancellationReasonEn;
        private String progressNotesEn;
        private String progressNotesAr;
        private String evidenceRefs;
    }
}
