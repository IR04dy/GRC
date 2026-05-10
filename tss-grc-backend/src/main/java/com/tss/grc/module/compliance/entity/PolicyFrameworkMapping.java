package com.tss.grc.module.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Many-to-many mapping: a Policy can satisfy multiple frameworks at the same
 * time (NCA ECC §1-1 + ISO 27001 A.5.1 + SAMA CSF 1.2.1, etc.). Each row
 * captures the specific clause/control reference and a satisfaction level.
 */
@Entity
@Table(schema = "compliance", name = "policy_framework_mappings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PolicyFrameworkMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

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
    private Long createdBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
