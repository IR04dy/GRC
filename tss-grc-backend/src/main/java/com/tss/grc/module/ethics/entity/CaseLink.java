package com.tss.grc.module.ethics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Cross-module link from a case to any other entity (risk, finding, control,
 * policy, change request, incident, vendor, evidence, DPIA). Same generic
 * pattern as {@code change_mgmt.change_links}.
 */
@Entity
@Table(schema = "ethics", name = "case_links")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CaseLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "link_type", nullable = false, length = 30)
    private String linkType = "RELATES_TO";

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
