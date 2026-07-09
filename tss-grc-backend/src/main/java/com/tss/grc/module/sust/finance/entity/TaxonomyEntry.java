package com.tss.grc.module.sust.finance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * Green-Finance uplift G3 (plan §5.1) — one versioned green-taxonomy category.
 * Ships with the ICMA GBP + Saudi CCE default set (organisation_id = 0); tenants
 * can add CUSTOM entries, and the whole set is swappable via a new version the
 * day an official Saudi green taxonomy is published.
 */
@Entity
@Table(schema = "sust", name = "taxonomy_entries")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TaxonomyEntry extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_ar", length = 200)
    private String nameAr;

    /** ICMA | CCE | NATIONAL | CUSTOM. */
    @Column(nullable = false, length = 20)
    private String source = "ICMA";

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
