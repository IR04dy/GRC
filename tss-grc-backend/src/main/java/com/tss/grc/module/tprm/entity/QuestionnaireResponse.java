package com.tss.grc.module.tprm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "tprm", name = "questionnaire_responses")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class QuestionnaireResponse {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "vendor_assessment_id", nullable = false) private Long vendorAssessmentId;
    @Column(name = "question_id", nullable = false)          private Long questionId;
    @Column(name = "template_id", nullable = false)          private Long templateId;

    @Column(name = "answer_value", length = 500)                          private String answerValue;
    @Column(name = "scored_points",   precision = 5, scale = 2)           private BigDecimal scoredPoints;
    @Column(name = "max_points",      nullable = false, precision = 5, scale = 2) private BigDecimal maxPoints;
    @Column(name = "weighted_points", precision = 8, scale = 2)           private BigDecimal weightedPoints;

    @Column(name = "answer_notes_en", columnDefinition = "NVARCHAR(MAX)") private String answerNotesEn;
    @Column(name = "answer_notes_ar", columnDefinition = "NVARCHAR(MAX)") private String answerNotesAr;
    @Column(name = "evidence_ref", length = 255)                          private String evidenceRef;
    @Column(name = "not_applicable",   nullable = false)                  private Boolean notApplicable;
    @Column(name = "na_justification", columnDefinition = "NVARCHAR(MAX)") private String naJustification;

    @Column(name = "answered_by", length = 120) private String answeredBy;
    @Column(name = "answered_at")               private LocalDateTime answeredAt;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null)     createdAt = LocalDateTime.now();
        if (maxPoints == null)     maxPoints = new BigDecimal("5.00");
        if (notApplicable == null) notApplicable = false;
        if (isDeleted == null)     isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
