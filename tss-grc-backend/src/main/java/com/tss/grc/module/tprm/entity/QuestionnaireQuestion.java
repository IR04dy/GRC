package com.tss.grc.module.tprm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "tprm", name = "questionnaire_questions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class QuestionnaireQuestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "template_id", nullable = false)     private Long templateId;

    @Column(name = "section_code",    nullable = false, length = 40) private String sectionCode;
    @Column(name = "section_name_en", length = 255)                  private String sectionNameEn;
    @Column(name = "section_name_ar", length = 255)                  private String sectionNameAr;

    @Column(name = "question_code", nullable = false, length = 40) private String questionCode;
    @Column(name = "question_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String questionEn;
    @Column(name = "question_ar", columnDefinition = "NVARCHAR(MAX)")                   private String questionAr;

    @Column(name = "answer_type",  nullable = false, length = 20) private String answerType;
    @Column(name = "options_json", columnDefinition = "NVARCHAR(MAX)") private String optionsJson;
    @Column(nullable = false, precision = 5, scale = 2)               private BigDecimal weight;
    @Column(name = "invert_score", nullable = false)                  private Boolean invertScore;
    @Column(nullable = false)                                          private Boolean mandatory;

    @Column(name = "sequence_order", nullable = false) private Integer sequenceOrder;
    @Column(name = "guidance_en", columnDefinition = "NVARCHAR(MAX)") private String guidanceEn;
    @Column(name = "guidance_ar", columnDefinition = "NVARCHAR(MAX)") private String guidanceAr;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null)     createdAt = LocalDateTime.now();
        if (answerType == null)    answerType = "SCALE_1_5";
        if (weight == null)        weight = BigDecimal.ONE;
        if (invertScore == null)   invertScore = false;
        if (mandatory == null)     mandatory = true;
        if (sequenceOrder == null) sequenceOrder = 100;
        if (isDeleted == null)     isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
