package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "ScmVendor")
@Table(schema = "scm", name = "vendors")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Vendor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "criticality", length = 20)
    private String criticality = "MEDIUM";

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "service_type", length = 100)
    private String serviceType;

    @Column(name = "risk_level", length = 20)
    private String riskLevel = "MEDIUM";

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "onboarding_status", length = 30)
    private String onboardingStatus = "PENDING";

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
