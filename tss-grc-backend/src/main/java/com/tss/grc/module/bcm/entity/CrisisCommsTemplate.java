package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "crisis_comms_templates")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisCommsTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    /** EXECUTIVE | EMPLOYEES | CUSTOMERS | REGULATORS | PUBLIC | ALL */
    @Column(name = "audience_role", nullable = false, length = 100)
    private String audienceRole;

    /** EMAIL | SMS | PUSH | TAWAKKALNA | IN_APP | MULTI */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "subject_en", length = 500)
    private String subjectEn;

    @Column(name = "subject_ar", length = 500)
    private String subjectAr;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "body_ar", columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    /** CRITICAL | HIGH | MEDIUM | LOW | ANY */
    @Column(name = "severity_band", length = 20)
    private String severityBand;

    @Column(name = "version", nullable = false, length = 20)
    private String version = "1.0";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
