package com.tss.grc.module.ethics.coi.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;

/**
 * Annual / ad-hoc Conflict-of-Interest declaration campaign — Sprint 13.
 *
 * <p>One row per declaration cycle. Spawned by the Ethics Officer or
 * Board Secretariat at the start of a fiscal year (or whenever an
 * ad-hoc re-attestation is required). Declarations
 * ({@link CoiDeclaration}) attach to this campaign and inherit its
 * scope.
 *
 * <h3>Status machine</h3>
 * <pre>
 *   DRAFT → OPEN → CLOSED → ARCHIVED
 * </pre>
 * The {@link CoiDeclarationCampaign#scope} field decides who is
 * obliged to declare: BOARD / EXEC / ALL_STAFF / HIGH_RISK_ROLES.
 */
@Entity
@Table(schema = "ethics", name = "coi_declaration_campaign")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CoiDeclarationCampaign extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "opens_on", nullable = false)
    private LocalDate opensOn;

    @Column(name = "closes_on", nullable = false)
    private LocalDate closesOn;

    /** DRAFT | OPEN | CLOSED | ARCHIVED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    /** BOARD | EXEC | ALL_STAFF | HIGH_RISK_ROLES. */
    @Column(name = "scope", nullable = false, length = 64)
    private String scope = "BOARD";

    @Column(name = "target_user_count", nullable = false)
    private Integer targetUserCount = 0;

    @Column(name = "declared_user_count", nullable = false)
    private Integer declaredUserCount = 0;
}
