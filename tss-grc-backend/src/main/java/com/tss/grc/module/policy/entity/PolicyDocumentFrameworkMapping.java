package com.tss.grc.module.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Canonical many-to-many mapping between a {@link PolicyDocument} and a compliance
 * framework (CH-6 / PM-C1, V302). Replaces the legacy
 * {@code compliance.policy_framework_mappings}, which keyed off the retired
 * {@code compliance.policies} register. Carries {@code organisation_id} directly so
 * framework-coverage tallies scope by tenant without a parent-policy subquery
 * (retiring the CH-15 join once FrameworkAnchorService repoints here in Phase B).
 */
@Entity
@Table(schema = "policy", name = "document_framework_mappings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PolicyDocumentFrameworkMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "framework_id", nullable = false)
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef; // e.g. "ECC §2-1-1", "A.5.1.1", "Art. 33"

    @Column(name = "satisfaction", nullable = false, length = 20)
    private String satisfaction = "PARTIAL"; // FULL | PARTIAL | OUT_OF_SCOPE | NOT_APPLICABLE

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
