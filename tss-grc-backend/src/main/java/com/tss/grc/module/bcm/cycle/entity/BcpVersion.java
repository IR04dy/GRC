package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Immutable version snapshot of a {@link BcpPlan} — audit T1.4b.
 *
 * <p>The service writes one row here every time a plan is APPROVED or
 * PUBLISHED. {@code bodyJson} carries the full plan + sections payload at
 * snapshot time so an auditor can diff v3 vs v1 without reconstructing
 * history from edits. Append-only — rows are never updated.
 *
 * <p>Shape of {@code bodyJson}:
 * <pre>{@code
 * {
 *   "plan": {
 *     "id": 42, "planCode": "BCP-2026-000123", "titleEn": "...",
 *     "rtoHours": 8, "rpoHours": 4, "mtpdHours": 24, ...
 *   },
 *   "sections": [
 *     {"sectionKey": "overview", "sectionTitleEn": "Plan overview",
 *      "bodyEn": "...", "bodyAr": "...", "sectionOrder": 1}, ...
 *   ]
 * }
 * }</pre>
 */
@Entity
@Table(schema = "bcm", name = "bcp_versions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpVersion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "body_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyJson;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
