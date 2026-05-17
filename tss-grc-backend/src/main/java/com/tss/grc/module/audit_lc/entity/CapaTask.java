package com.tss.grc.module.audit_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Corrective / preventive action — V16 table {@code audit_lc.capa_tasks}.
 *
 * <p>Each CAPA hangs off a {@link Finding} by the finding's external_id.
 * Status: OPEN → IN_PROGRESS → AWAITING_VERIFICATION → CLOSED. OVERDUE
 * is computed/set by the service when target_date is past.
 */
@Entity(name = "AuditLcCapaTask")
@Table(schema = "audit_lc", name = "capa_tasks")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CapaTask {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false)          private Long organisationId;

    /** Parent finding's external_id (e.g. FIN-2026-001). */
    @Column(name = "finding_id", nullable = false, length = 64) private String findingId;

    /** CORRECTIVE | PREVENTIVE */
    @Column(nullable = false, length = 15) private String kind;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;

    @Column(name = "owner_role", nullable = false) private String ownerRole;
    @Column(name = "target_date", nullable = false) private LocalDate targetDate;

    /** OPEN | IN_PROGRESS | AWAITING_VERIFICATION | CLOSED | OVERDUE */
    @Column(nullable = false, length = 30) private String status = "OPEN";

    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "verification_notes_en", columnDefinition = "NVARCHAR(MAX)") private String verificationNotesEn;
    @Column(name = "verification_notes_ar", columnDefinition = "NVARCHAR(MAX)") private String verificationNotesAr;

    @Column(name = "evidence_ref", length = 64) private String evidenceRef;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
