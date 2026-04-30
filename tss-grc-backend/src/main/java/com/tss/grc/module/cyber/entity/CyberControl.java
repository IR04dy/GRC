package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_controls")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "framework_name")
    private String frameworkName;

    private String domain;

    @Column(name = "control_number")
    private String controlNumber;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "title_ar")
    private String titleAr;

    @Column(name = "procedure_en", columnDefinition = "NVARCHAR(MAX)")
    private String procedureEn;

    @Column(name = "control_type")
    private String controlType;

    @Column(name = "control_owner")
    private String controlOwner;

    @Column(name = "review_frequency")
    private String reviewFrequency;

    @Column(name = "maturity_level")
    private String maturityLevel;

    @Column(name = "implementation_status")
    private String implementationStatus;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
