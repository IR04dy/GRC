package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(schema = "privacy", name = "dpia_question")
@Getter @Setter @NoArgsConstructor
public class DpiaQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dpia_id", nullable = false)
    private Long dpiaId;

    @Column(name = "question_code", length = 50)
    private String questionCode;

    @Column(name = "question_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String questionEn;

    @Column(name = "question_ar", columnDefinition = "NVARCHAR(MAX)")
    private String questionAr;

    @Column(name = "answer", columnDefinition = "NVARCHAR(MAX)")
    private String answer;

    /** CRITICAL | HIGH | MEDIUM | LOW | NA */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
