package com.tss.grc.module.sust.ghg.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Versioned emission-factor library entry (V253 — S-J).
 *
 * <p>A per-tenant reference factor: an {@code activityUnit} (L, kWh, km …) →
 * {@code factorValue} in {@code factorUnit} (kgCO2e/L, tCO2e/kWh …) from a
 * cited {@code source} (IPCC 2019, SEEC KSA grid factor …). Creating a new
 * version of a {@code factorCode} deactivates the prior active row and stamps
 * its {@code validTo} — GHG records reference the active version instead of
 * hand-typing the number.
 */
@Entity
@Table(schema = "sust", name = "emission_factor")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EmissionFactor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "factor_code", nullable = false, length = 60) private String factorCode;

    @Column(nullable = false)              private Short scope;
    @Column(length = 60)                   private String category;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(name = "activity_unit", nullable = false, length = 40) private String activityUnit;
    @Column(name = "factor_value", nullable = false, precision = 20, scale = 6) private BigDecimal factorValue;
    @Column(name = "factor_unit", nullable = false, length = 40)   private String factorUnit;

    @Column(length = 255) private String source;

    @Column(nullable = false)                 private Integer version = 1;
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    @Column(name = "valid_from")              private LocalDate validFrom;
    @Column(name = "valid_to")                private LocalDate validTo;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
