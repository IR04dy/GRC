package com.tss.grc.module.compliance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(schema = "compliance", name = "assessments")
@Getter @Setter @NoArgsConstructor
public class ComplianceAssessment extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "framework", nullable = false, length = 100)
    private String framework;

    @Column(name = "period", length = 100)
    private String period;

    @Column(name = "auditor", length = 255)
    private String auditor;

    @Column(name = "score", columnDefinition = "DECIMAL(5,2)")
    private Double score;

    @Column(name = "status", length = 50)
    private String status = "PLANNED";

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;
}
