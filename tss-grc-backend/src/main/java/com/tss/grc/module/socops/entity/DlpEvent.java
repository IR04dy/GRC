package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Data Loss Prevention event — one row per Microsoft Purview / Forcepoint
 * / Symantec / Netskope / Proofpoint policy trigger. {@code dataClassification}
 * tracks the Saudi NDC level (TOP_SECRET / SECRET / RESTRICTED / PUBLIC)
 * which drives PDPL Article 5 evidence + cross-border-transfer review.
 */
@Entity
@Table(schema = "socops", name = "dlp_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DlpEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, length = 120) private String externalId;
    @Column(name = "source_system", nullable = false, length = 40) private String sourceSystem;
    @Column(name = "event_time", nullable = false) private LocalDateTime eventTime;
    @Column(name = "policy_name", length = 300) private String policyName;
    @Column(name = "policy_category", length = 80) private String policyCategory;
    @Column(name = "data_classification", length = 40) private String dataClassification;
    @Column(length = 255) private String username;
    @Column(name = "user_email", length = 255) private String userEmail;
    @Column(length = 120) private String department;
    @Column(nullable = false, length = 40) private String channel;
    @Column(length = 500) private String destination;
    @Column(name = "file_name", length = 500) private String fileName;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(name = "file_hash", length = 128) private String fileHash;
    @Column(name = "match_count") private Integer matchCount;
    @Column(length = 20) private String severity;
    @Column(name = "action_taken", nullable = false, length = 30) private String actionTaken;
    @Column(nullable = false) private Boolean reviewed = false;
    @Column(name = "review_outcome", length = 40) private String reviewOutcome;
    @Column(name = "review_notes", columnDefinition = "NVARCHAR(MAX)") private String reviewNotes;
    @Column(name = "raw_payload", columnDefinition = "NVARCHAR(MAX)") private String rawPayload;
}
