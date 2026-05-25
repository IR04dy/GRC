package com.tss.grc.module.reginspect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Sprint 15 PR #1 — Regulatory Inspection Workspace DTOs. */
public final class RegInspectDtos {
    private RegInspectDtos() {}

    // ─── Inspection ────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateInspectionRequest {
        @NotBlank @Size(max = 16)
        private String regulator;          // SAMA | NCA | CMA | SDAIA | NCEMA | OTHER

        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;

        private LocalDate notifiedAt;
        private LocalDate entryDate;
        private LocalDate exitDate;

        @Size(max = 256) private String leadInspectorName;
        @Size(max = 4000) private String scopeEn;
        @Size(max = 4000) private String scopeAr;

        private Long coordinatorUserId;
        private Long sponsorUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateInspectionRequest {
        @Size(max = 256) private String titleEn;
        @Size(max = 256) private String titleAr;
        private LocalDate notifiedAt;
        private LocalDate entryDate;
        private LocalDate exitDate;
        @Size(max = 256) private String leadInspectorName;
        @Size(max = 4000) private String scopeEn;
        @Size(max = 4000) private String scopeAr;
        private Long coordinatorUserId;
        private Long sponsorUserId;
        @Size(max = 4000) private String executiveSummaryEn;
        @Size(max = 4000) private String executiveSummaryAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionRequest {
        /** Target status — must be the next state per the lifecycle. */
        @NotBlank @Size(max = 32) private String toStatus;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InspectionResponse {
        private Long id;
        private String externalReference;
        private String regulator;
        private String titleEn;
        private String titleAr;
        private String status;
        private LocalDate notifiedAt;
        private LocalDate entryDate;
        private LocalDate exitDate;
        private LocalDateTime closedAt;
        private String leadInspectorName;
        private String scopeEn;
        private String scopeAr;
        private Long coordinatorUserId;
        private Long sponsorUserId;
        private String executiveSummaryEn;
        private String executiveSummaryAr;
        private Integer openDrCount;
        private Integer overdueDrCount;
        private Integer submittedDrCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Document request ──────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateDocumentRequestRequest {
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @NotNull private LocalDate dueDate;
        private Long assignedToUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitDocumentRequestRequest {
        @NotBlank @Size(max = 512) private String evidenceReference;
        @Size(max = 4000) private String submissionNotesEn;
        @Size(max = 4000) private String submissionNotesAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentRequestResponse {
        private Long id;
        private Long inspectionId;
        private String reference;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private String status;
        private LocalDateTime requestedAt;
        private LocalDate dueDate;
        private LocalDateTime submittedAt;
        private Long assignedToUserId;
        private String evidenceReference;
        private String submissionNotesEn;
        private String submissionNotesAr;
        /** True iff status != SUBMITTED and dueDate &lt; today. */
        private Boolean overdue;
    }

    // ─── Timeline ───────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InspectionUpdateResponse {
        private Long id;
        private Long inspectionId;
        private LocalDateTime occurredAt;
        private String updateType;
        private Long actorUserId;
        private String actorName;
        private String statusAfter;
        private String noteEn;
        private String noteAr;
        private Long linkedDocumentRequestId;
    }

    // ─── Dashboard ──────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        /** Inspections in PLANNED + ENTRY_MEETING + FIELDWORK + EXIT_MEETING + RESPONSE_DRAFTING. */
        private long activeCount;
        private long closedCount;
        private long openDrCount;
        private long overdueDrCount;
        private List<InspectionResponse> recentInspections;
    }
}
