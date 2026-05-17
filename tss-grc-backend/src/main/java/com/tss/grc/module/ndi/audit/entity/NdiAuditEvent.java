package com.tss.grc.module.ndi.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "ndi", name = "audit_event")
@Getter @Setter @NoArgsConstructor
public class NdiAuditEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "cycle_id")                          private Long cycleId;

    @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
    @Column(name = "entity_id")                                  private Long entityId;
    @Column(name = "entity_code", length = 80)                   private String entityCode;

    @Column(nullable = false, length = 30) private String action;
    @Column(name = "from_state", length = 40) private String fromState;
    @Column(name = "to_state",   length = 40) private String toState;
    @Column(nullable = false, length = 20) private String severity;

    @Column(name = "actor_username", length = 120) private String actorUsername;
    @Column(name = "actor_role",     length = 80)  private String actorRole;
    @Column(name = "actor_ip",       length = 50)  private String actorIp;

    @Column(length = 500)                       private String summary;
    @Column(name = "diff_json", columnDefinition = "NVARCHAR(MAX)") private String diffJson;
    @Column(name = "correlation_id", length = 64) private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false) private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
        if (severity == null)   severity = "INFO";
    }
}
