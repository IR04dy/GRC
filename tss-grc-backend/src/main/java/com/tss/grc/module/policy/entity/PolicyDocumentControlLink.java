package com.tss.grc.module.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * C-12 — a normalised many-to-many link between a {@link PolicyDocument} and a
 * compliance control ({@code compliance.controls}). Replaces the free-text
 * {@code policy.documents.related_controls} with real FK references (the
 * free-text column is retained, deprecated, during the expand-contract window).
 */
@Entity
@Table(schema = "policy", name = "document_control_links")
@Getter @Setter @NoArgsConstructor
public class PolicyDocumentControlLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)    private Long organisationId;
    @Column(name = "policy_document_id", nullable = false) private Long policyDocumentId;
    @Column(name = "control_id", nullable = false)         private Long controlId;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public PolicyDocumentControlLink(Long organisationId, Long policyDocumentId, Long controlId) {
        this.organisationId = organisationId;
        this.policyDocumentId = policyDocumentId;
        this.controlId = controlId;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
