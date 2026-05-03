package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Dependency edge between two critical processes — M3 BIA dependency graph.
 *
 * (process_id) is the consumer/dependent process; (depends_on_process_id) is
 * the upstream producer. Loss of the upstream propagates to the consumer.
 */
@Entity
@Table(schema = "bcm", name = "process_dependencies")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ProcessDependency {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "depends_on_process_id", nullable = false)
    private Long dependsOnProcessId;

    @Column(name = "dependency_type", nullable = false, length = 40)
    private String dependencyType = "INTERNAL_PROCESS";

    @Column(name = "criticality_impact", length = 20)
    private String criticalityImpact;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
