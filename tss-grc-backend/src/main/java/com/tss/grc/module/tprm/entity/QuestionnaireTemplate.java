package com.tss.grc.module.tprm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * TPRM Questionnaire Template — V81.
 *
 * <p>Reusable bundle of due-diligence questions. Status machine:
 * DRAFT → ACTIVE → ARCHIVED. Questions can only be added/edited while
 * the template is DRAFT — once ACTIVE, snapshot is frozen so the
 * audit history of past vendor assessments stays consistent.
 */
@Entity
@Table(schema = "tprm", name = "questionnaire_templates")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class QuestionnaireTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "template_code", nullable = false, length = 40) private String templateCode;
    @Column(nullable = false, length = 20)                          private String version;

    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(name = "name_ar", length = 255)                   private String nameAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(nullable = false, length = 40)        private String category;
    @Column(name = "applicable_to", columnDefinition = "NVARCHAR(MAX)") private String applicableTo;

    @Column(name = "threshold_low_max",    nullable = false) private Integer thresholdLowMax;
    @Column(name = "threshold_medium_max", nullable = false) private Integer thresholdMediumMax;
    @Column(name = "threshold_high_max",   nullable = false) private Integer thresholdHighMax;

    @Column(nullable = false, length = 20) private String status;
    @Column(name = "activated_at") private LocalDateTime activatedAt;
    @Column(name = "archived_at")  private LocalDateTime archivedAt;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)          createdAt = LocalDateTime.now();
        if (version == null)            version = "1.0";
        if (status == null)             status = "DRAFT";
        if (category == null)           category = "GENERAL";
        if (thresholdLowMax == null)    thresholdLowMax = 30;
        if (thresholdMediumMax == null) thresholdMediumMax = 60;
        if (thresholdHighMax == null)   thresholdHighMax = 80;
        if (isDeleted == null)          isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
