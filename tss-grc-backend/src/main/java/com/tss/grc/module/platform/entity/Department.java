package com.tss.grc.module.platform.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * Department — an organisational chart unit owned by a single tenant.
 *
 * <p>Materialises the {@code department_id} soft FK that several existing
 * tables (users, BCM communication contacts, audit findings, etc.) carry.
 * Self-FK via {@code parentId} so multi-level hierarchies are representable
 * without changing the table.
 *
 * <p>Soft-deleted via {@code is_deleted} with the platform-wide @Where filter.
 */
@Entity
@Table(schema = "platform", name = "departments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Department extends BaseEntity {

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

    /** Self-FK — null for root departments. Soft FK; no JPA mapping to avoid eager loads. */
    @Column(name = "parent_id")
    private Long parentId;

    /** Soft FK to iam.users — the department head. */
    @Column(name = "head_user_id")
    private Long headUserId;

    @Column(name = "cost_centre", length = 50)
    private String costCentre;

    @Column(name = "description", length = 1000)
    private String description;

    /** ACTIVE | INACTIVE | ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
}
