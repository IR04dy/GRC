package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_incidents")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "incident_number")
    private String incidentNumber;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "title_ar")
    private String titleAr;

    @Column(name = "incident_type")
    private String incidentType;

    @Column(name = "affected_asset_id")
    private Long affectedAssetId;

    @Column(name = "affected_asset_name")
    private String affectedAssetName;

    private String impact;

    private String severity;

    @Column(name = "workflow_status", nullable = false)
    private String workflowStatus = "DETECTION";

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "incident_lead")
    private String incidentLead;

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "bcm_handoff")
    private Boolean bcmHandoff = false;

    @Column(name = "linked_vulnerabilities_count")
    private int linkedVulnerabilitiesCount;

    @Column(name = "linked_evidence_count")
    private int linkedEvidenceCount;

    @Column(name = "lessons_learned", columnDefinition = "NVARCHAR(MAX)")
    private String lessonsLearned;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
