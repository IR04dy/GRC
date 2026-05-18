package com.tss.grc.module.evidence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * §5.9 #2 — Evidence connector registry (V115).
 *
 * <p>The buyer assessment listed "Connector library MVP — ship 8-10 stub
 * connector definitions so prospects can see what we can integrate with
 * before they ask." This entity is the catalog row; the actual ingestion
 * path is still the existing SIEM-webhook intake + manual upload. Per-
 * tenant enablement + configuration lives in {@code config_json}.
 *
 * <p>Status lifecycle:
 *   DRAFT       — being authored, not visible to tenants
 *   AVAILABLE   — visible in connector picker, not enabled
 *   ENABLED     — tenant turned it on; sync attempts will run
 *   DISABLED    — tenant turned it off (keeps history)
 *   DEPRECATED  — vendor sunset; new tenants can't enable it
 */
@Entity
@Table(schema = "evidence", name = "evidence_connectors")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EvidenceConnector {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_ar", length = 200)
    private String nameAr;

    @Column(length = 100)
    private String vendor;

    /** IDENTITY | ENDPOINT | CLOUD | VULN | NETWORK | SIEM | DEVOPS | OTHER */
    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "capability_json", columnDefinition = "NVARCHAR(MAX)")
    private String capabilityJson;

    /** DRAFT | AVAILABLE | ENABLED | DISABLED | DEPRECATED */
    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(length = 20)
    private String version;

    @Column(name = "config_json", columnDefinition = "NVARCHAR(MAX)")
    private String configJson;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /** OK | ERROR | NEVER */
    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    @Column(name = "last_sync_note", length = 500)
    private String lastSyncNote;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
