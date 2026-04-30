package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "scm", name = "dependencies")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Dependency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "service_name_en", nullable = false, length = 255)
    private String serviceNameEn;

    @Column(name = "service_name_ar", length = 255)
    private String serviceNameAr;

    @Column(name = "asset_name", length = 255)
    private String assetName;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "dependency_type", length = 50)
    private String dependencyType;

    @Column(name = "dependency_level", length = 20)
    private String dependencyLevel = "MEDIUM";

    @Column(name = "has_alternative", nullable = false)
    private Boolean hasAlternative = false;

    @Column(name = "alternative_vendor_id")
    private Long alternativeVendorId;

    @Column(name = "status", length = 30)
    private String status = "ACTIVE";

    @Column(name = "impact_analysis", columnDefinition = "NVARCHAR(MAX)")
    private String impactAnalysis;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
