package com.tss.grc.module.platform.auditlog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generic platform-wide audit log row — Framework Intersection F7.
 *
 * <p>Mirrors the audit-trail requirements in the Saudi GRC prompt
 * §29: who/when/what/where/before/after/correlation. Module-
 * specific audit tables (ndi.audit_events, workflow.action_logs)
 * keep their own shapes for domain-specific rules; this row exists
 * so the unified viewer at /admin/audit-log/unified can show every
 * event across modules.
 */
@Entity
@Table(schema = "platform", name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLogEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_username", length = 120)
    private String userUsername;

    @Column(name = "user_role", length = 60)
    private String userRole;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "entity_type", length = 120)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_external_id", length = 60)
    private String entityExternalId;

    @Column(name = "entity_code", length = 120)
    private String entityCode;

    @Column(name = "old_value_json", columnDefinition = "NVARCHAR(MAX)")
    private String oldValueJson;

    @Column(name = "new_value_json", columnDefinition = "NVARCHAR(MAX)")
    private String newValueJson;

    @Column(name = "workflow_status_before", length = 40)
    private String workflowStatusBefore;

    @Column(name = "workflow_status_after", length = 40)
    private String workflowStatusAfter;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "source_module", length = 60)
    private String sourceModule;

    @Column(name = "source_endpoint", length = 255)
    private String sourceEndpoint;

    @Column(name = "correlation_id", length = 60)
    private String correlationId;

    @Column(name = "parent_audit_id")
    private Long parentAuditId;

    @Column(name = "digital_signature_ref", length = 120)
    private String digitalSignatureRef;

    /** INFO | WARN | ERROR */
    @Column(nullable = false, length = 20)
    private String severity = "INFO";

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
