package com.tss.grc.module.compliance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "compliance", name = "findings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AuditFinding extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "capa", columnDefinition = "NVARCHAR(MAX)")
    private String capa;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", length = 50)
    private String status = "OPEN";

    // Framework anchor (IA-2) — typically the framework this finding originated in.
    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

    // ── CAPA closure verification (assessment §5.3 #6) ────────────────────────
    // Stage flow: IDENTIFIED → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_TESTED → AUDIT_SIGNED_OFF → CLOSED
    @Column(name = "capa_stage", length = 40)
    private String capaStage = "IDENTIFIED";

    @Column(name = "owner_test_at")
    private LocalDateTime ownerTestAt;

    @Column(name = "owner_test_result", length = 20)
    private String ownerTestResult; // PASS|FAIL|PARTIAL

    @Column(name = "owner_test_evidence_id")
    private Long ownerTestEvidenceId;

    @Column(name = "owner_test_notes", columnDefinition = "NVARCHAR(MAX)")
    private String ownerTestNotes;

    @Column(name = "independent_test_at")
    private LocalDateTime independentTestAt;

    @Column(name = "independent_test_by_id")
    private Long independentTestById;

    @Column(name = "independent_test_result", length = 20)
    private String independentTestResult;

    @Column(name = "independent_test_notes", columnDefinition = "NVARCHAR(MAX)")
    private String independentTestNotes;

    @Column(name = "audit_signoff_at")
    private LocalDateTime auditSignoffAt;

    @Column(name = "audit_signoff_by_id")
    private Long auditSignoffById;

    @Column(name = "audit_signoff_notes", columnDefinition = "NVARCHAR(MAX)")
    private String auditSignoffNotes;
}
