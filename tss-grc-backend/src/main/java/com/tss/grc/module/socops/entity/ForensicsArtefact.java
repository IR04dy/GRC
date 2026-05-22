package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Forensic artefact + chain of custody. Captures the integrity hash trail
 * for memory dumps / disk images / packet captures / log exports that
 * legal counsel may present in court. Distinct from the document-shaped
 * evidence module because forensic artefacts demand cryptographic
 * integrity and immutable chain-of-custody handlers.
 */
@Entity
@Table(schema = "socops", name = "forensics_artefact")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ForensicsArtefact extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "artefact_code", nullable = false, length = 80) private String artefactCode;
    @Column(name = "case_code", length = 80) private String caseCode;
    @Column(name = "related_incident_id") private Long relatedIncidentId;
    @Column(name = "related_crisis_event_id") private Long relatedCrisisEventId;
    @Column(name = "artefact_type", nullable = false, length = 40) private String artefactType;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "source_host", length = 255) private String sourceHost;
    @Column(name = "source_asset_id") private Long sourceAssetId;
    @Column(name = "captured_at", nullable = false) private LocalDateTime capturedAt;
    @Column(name = "captured_by_handler", nullable = false, length = 255) private String capturedByHandler;
    @Column(name = "capture_tool", length = 200) private String captureTool;
    @Column(name = "capture_method", length = 200) private String captureMethod;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(name = "md5_hash", length = 64) private String md5Hash;
    @Column(name = "sha256_hash", length = 128) private String sha256Hash;
    @Column(name = "sha512_hash", length = 256) private String sha512Hash;
    @Column(name = "integrity_verified_at") private LocalDateTime integrityVerifiedAt;
    @Column(name = "integrity_status", nullable = false, length = 30) private String integrityStatus = "VERIFIED";
    @Column(name = "current_custodian", length = 255) private String currentCustodian;
    @Column(name = "current_location", length = 300) private String currentLocation;
    @Column(name = "retention_until") private LocalDateTime retentionUntil;
    @Column(name = "disposed_at") private LocalDateTime disposedAt;
    @Column(name = "disposal_method", length = 200) private String disposalMethod;
    @Column(name = "legal_hold", nullable = false) private Boolean legalHold = false;
    @Column(nullable = false) private Boolean privileged = false;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
