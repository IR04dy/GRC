package com.tss.grc.module.opres.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Sprint 14 PR #8 — Operational-Resilience self-assessment DTOs. */
public final class OpResDtos {
    private OpResDtos() {}

    // ─── Pillar (catalog) ─────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PillarResponse {
        private Long id;
        private String code;
        private Integer sortOrder;
        private String nameEn;
        private String nameAr;
        private String descriptionEn;
        private String descriptionAr;
        private String samaReference;
        private String maturityRubricJson;
        private Boolean isActive;
    }

    // ─── Assessment ───────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateAssessmentRequest {
        @NotNull @Min(2000) @Max(2100)
        private Integer fiscalYear;

        @NotBlank @Size(max = 256)
        private String titleEn;

        @NotBlank @Size(max = 256)
        private String titleAr;

        private LocalDate dueDate;
        private Long sponsorUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateAssessmentRequest {
        @Size(max = 256) private String titleEn;
        @Size(max = 256) private String titleAr;
        private LocalDate dueDate;
        private Long sponsorUserId;
        @Size(max = 4000) private String executiveSummaryEn;
        @Size(max = 4000) private String executiveSummaryAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CellUpsertRequest {
        @NotNull private Long pillarId;
        @Min(1) @Max(5) private Integer currentScore;
        @Min(1) @Max(5) private Integer targetScore;
        @Size(max = 4000) private String rationaleEn;
        @Size(max = 4000) private String rationaleAr;
        @Size(max = 512)  private String evidenceReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CellResponse {
        private Long id;
        private Long assessmentId;
        private Long pillarId;
        private String pillarCodeSnapshot;
        private Integer currentScore;
        private Integer targetScore;
        private String rationaleEn;
        private String rationaleAr;
        private String evidenceReference;
        private Long lastEditedByUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssessmentResponse {
        private Long id;
        private Integer fiscalYear;
        private String titleEn;
        private String titleAr;
        private String status;
        private LocalDate dueDate;
        private Long sponsorUserId;
        private BigDecimal avgCurrentScore;
        private BigDecimal avgTargetScore;
        private String completeness;
        private LocalDateTime submittedAt;
        private LocalDateTime approvedAt;
        private Long approvedByUserId;
        private LocalDateTime closedAt;
        private String executiveSummaryEn;
        private String executiveSummaryAr;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<CellResponse> cells;
    }
}
