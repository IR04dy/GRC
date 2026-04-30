package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "bia_records")
@Getter @Setter @NoArgsConstructor
public class BiaRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "rto_hours")
    private Integer rtoHours;

    @Column(name = "rpo_hours")
    private Integer rpoHours;

    @Column(name = "mtpd_hours")
    private Integer mtpdHours;

    @Column(name = "overall_impact", length = 50)
    private String overallImpact;

    @Column(name = "status", length = 50)
    private String status = "DRAFT";

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
