package com.tss.grc.module.ndi.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * NDI Data Governance Domain — slice 6.
 *
 * <p>Master catalog of governance domains used to organise the NDI
 * assessment and to roll up maturity / compliance / opex scores into a
 * weighted overall NDI index.
 *
 * <p>Domains here are PLACEHOLDER until an official SDAIA/NDI domain
 * library is imported; the UI surfaces a bilingual banner on every row
 * where {@code isPlaceholder = 1}.
 */
@Entity
@Table(schema = "ndi", name = "governance_domain")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiGovernanceDomain {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(nullable = false, length = 40)  private String code;
    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(name = "name_ar", length = 255)                   private String nameAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "domain_aliases", columnDefinition = "NVARCHAR(MAX)") private String domainAliases;

    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal weight;
    @Column(name = "sort_order", nullable = false)      private Integer sortOrder;

    @Column(name = "weight_maturity",   nullable = false, precision = 5, scale = 4) private BigDecimal weightMaturity;
    @Column(name = "weight_compliance", nullable = false, precision = 5, scale = 4) private BigDecimal weightCompliance;
    @Column(name = "weight_opex",       nullable = false, precision = 5, scale = 4) private BigDecimal weightOpex;

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;

    @Column(name = "is_active",      nullable = false) private Boolean isActive;
    @Column(name = "is_placeholder", nullable = false) private Boolean isPlaceholder;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    /**
     * Defaults are applied here (NOT as field initialisers) so that an
     * update DTO deserialised through Jackson — which would otherwise
     * carry these defaults and clobber existing rows when the service
     * patches in non-null values — leaves these fields null and lets
     * the service decide whether to touch them.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null)        createdAt        = LocalDateTime.now();
        if (weight == null)           weight           = new BigDecimal("10.00");
        if (sortOrder == null)        sortOrder        = 100;
        if (weightMaturity == null)   weightMaturity   = new BigDecimal("0.3500");
        if (weightCompliance == null) weightCompliance = new BigDecimal("0.4000");
        if (weightOpex == null)       weightOpex       = new BigDecimal("0.2500");
        if (isActive == null)         isActive         = true;
        if (isPlaceholder == null)    isPlaceholder    = true;
        if (isDeleted == null)        isDeleted        = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
