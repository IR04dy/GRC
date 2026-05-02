package com.tss.grc.module.workflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "workflow", name = "action_log")
@Getter @Setter @NoArgsConstructor
public class WorkflowActionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "transition_id")
    private Long transitionId;

    @Column(name = "action_code", nullable = false, length = 50)
    private String actionCode;

    @Column(name = "from_state_id")
    private Long fromStateId;

    @Column(name = "to_state_id", nullable = false)
    private Long toStateId;

    @Column(name = "executed_by_id")
    private Long executedById;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();

    @Column(name = "comments", columnDefinition = "NVARCHAR(MAX)")
    private String comments;
}
