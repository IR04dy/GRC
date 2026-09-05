package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "scm", name = "supplier_incidents")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SupplierIncident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "incident_code", length = 20)
    private String incidentCode;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "incident_type", length = 50)
    private String incidentType;

    @Column(name = "impact", length = 20)
    private String impact = "MEDIUM";

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "sla_breached", nullable = false)
    private Boolean slaBreached = false;

    @Column(name = "status", length = 30)
    private String status = "OPEN";

    @Column(name = "backup_plan_activated", nullable = false)
    private Boolean backupPlanActivated = false;

    @Column(name = "capa_created", nullable = false)
    private Boolean capaCreated = false;

    @Column(name = "escalated_to_management", nullable = false)
    private Boolean escalatedToManagement = false;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "resolution_notes", columnDefinition = "NVARCHAR(MAX)")
    private String resolutionNotes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
