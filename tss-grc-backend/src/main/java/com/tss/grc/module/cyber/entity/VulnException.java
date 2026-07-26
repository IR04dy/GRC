package com.tss.grc.module.cyber.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "vuln_exceptions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VulnException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vulnerability_id")
    private Long vulnerabilityId;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "exception_reason", columnDefinition = "NVARCHAR(MAX)")
    private String exceptionReason;

    @Column(name = "workflow_status", nullable = false)
    private String workflowStatus = "REQUESTED";

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "approver_name")
    private String approverName;

    @Column(name = "compensating_controls", columnDefinition = "NVARCHAR(MAX)")
    private String compensatingControls;

    @Column(name = "impact_assessment", columnDefinition = "NVARCHAR(MAX)")
    private String impactAssessment;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "approved_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDate approvedAt;

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
