package com.tss.grc.module.workflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "workflow", name = "instances")
@Getter @Setter @NoArgsConstructor
public class WorkflowInstance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "target_module", nullable = false, length = 50)
    private String targetModule;

    @Column(name = "target_entity", nullable = false, length = 100)
    private String targetEntity;

    @Column(name = "target_entity_id", nullable = false)
    private Long targetEntityId;

    @Column(name = "current_state_id", nullable = false)
    private Long currentStateId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_transition_at")
    private LocalDateTime lastTransitionAt;
}
