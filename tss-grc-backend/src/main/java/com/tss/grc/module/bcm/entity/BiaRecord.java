package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ISO 22301 §8.2 Business Impact Analysis record.
 *
 * <h2>⚠⚠ F2-279 — this entity mapped 11 of its table's 29 columns</h2>
 * <p>{@code bcm.bia_records} was created with all 29 columns by {@code V1__core_schemas.sql},
 * the <em>first</em> migration, and the column order there is the fingerprint: V1's first
 * eleven columns are exactly the eleven this class used to declare, in this class's order,
 * and everything it omitted follows after. The table was extended and the entity never
 * followed. Two of the omissions were the two columns the platform's conventions are
 * built on, and each cost a live defect (all probed 2026-08-22, dev = prod):
 * <ul>
 *   <li><b>{@code organisation_id}</b> — {@code BIGINT NOT NULL DEFAULT ((1))}. Hibernate
 *       never named an unmapped column in its INSERT, so <b>the database default decided
 *       every row's tenant</b>, not the caller's {@code TenantContext}. Worse, nothing
 *       consulted the column on the way out either: a planted row whose own
 *       {@code organisation_id} said 2 was <b>served to an org-1 caller</b>, because
 *       {@code listBia} scoped by the parent process link instead. And because
 *       {@code TenantWriteGuardInterceptor} bails at {@code orgIdx < 0} — "Entity doesn't
 *       have an organisationId — out of scope" — this register sat outside the
 *       platform-wide tenant write backstop <b>by construction</b>.
 *       🔑 A guard's population is its question, and an entity that omits the column is
 *       not in it. Of 432 shipped entities, 35 declare no {@code organisationId} and
 *       <b>exactly one</b> of those mapped a table that has the column: this one.
 *   <li><b>{@code is_deleted}</b> — {@code BIT NOT NULL DEFAULT ((0))} with an index
 *       {@code ix_bia_records_org (organisation_id, is_deleted)} that {@code V228} built
 *       over <em>both</em> unmapped columns and nothing has ever used. With the column
 *       unmapped and no {@code @Where}, {@code deleteBia} wrote {@code status = 'DELETED'}
 *       instead and no query filtered it, so <b>{@code DELETE} answered 204 and the record
 *       stayed in the list</b> — rendered as a BIA whose status is the word DELETED. A row
 *       carrying the platform's own {@code is_deleted = 1} flag was served too.
 * </ul>
 * <p>⚠ {@code isDeleted} needs no scrubbing on the entity-bound create: it is one of
 * {@link com.tss.grc.common.json.ServerOwnedFieldsModule}'s nine server-owned fields, which
 * that introspector reports {@code READ_ONLY} for on <em>any</em> {@code @Entity}. That same
 * javadoc records that it deliberately "does not touch {@code organisationId}", which is why
 * the tenant is stamped explicitly in {@code BcmService#createBia} instead.
 * <p>⚠ The remaining <b>17</b> unmapped columns are a separate, filed finding — they hold
 * real data ({@code financial_impact}/{@code operational_impact}/{@code regulatory_impact}/
 * {@code reputational_impact}, the four ISO 22301 impact dimensions, on 7 of 10 live rows;
 * {@code owner_name} and {@code business_unit} on 10 of 10) that no wire can carry, and
 * {@code criticality} is {@code NOT NULL DEFAULT ('MEDIUM')}, so a created BIA silently
 * disagrees with its own mapped {@code overall_impact}. Mapping them is a product change.
 */
@Entity
@Table(schema = "bcm", name = "bia_records")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BiaRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /**
     * The owning tenant. Stamped by {@code BcmService#createBia} from {@code TenantContext}
     * and never taken from the request body — see the class javadoc.
     */
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "rto_hours")
    private Integer rtoHours;

    @Column(name = "rpo_hours")
    private Integer rpoHours;

    @Column(name = "mtpd_hours")
    private Integer mtpdHours;

    @Column(name = "overall_impact", length = 20)
    private String overallImpact;

    @Column(name = "status", length = 20)
    private String status = "DRAFT";

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
