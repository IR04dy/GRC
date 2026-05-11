package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_frameworks")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberFramework {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private String name;

    private String version;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    // ─── Bilingual + metadata columns (V37) ──────────────────────────
    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** NCA | SAMA | CITC | SDAIA | ISO | PCI. */
    @Column(name = "regulator", length = 20)
    private String regulator;

    /** ECC | CSCC | CCC | OTCC | ISO27001 | ISO22301 | PCIDSS | SAMA_CYBER. */
    @Column(name = "framework_code", length = 20)
    private String frameworkCode;

    @Column(name = "domain_count")
    private Integer domainCount;

    @Column(name = "control_count")
    private Integer controlCount;

    @Column(name = "effective_from")
    private java.time.LocalDate effectiveFrom;

    /** CSV of sectors / scopes — CRITICAL_INFRA,ENERGY,BANKING,etc. */
    @Column(name = "mandatory_for", columnDefinition = "NVARCHAR(MAX)")
    private String mandatoryFor;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

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
