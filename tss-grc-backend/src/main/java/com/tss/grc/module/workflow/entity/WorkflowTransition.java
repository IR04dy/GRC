package com.tss.grc.module.workflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(schema = "workflow", name = "transitions")
@Getter @Setter @NoArgsConstructor
public class WorkflowTransition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "from_state_id", nullable = false)
    private Long fromStateId;

    @Column(name = "to_state_id", nullable = false)
    private Long toStateId;

    @Column(name = "action_code", nullable = false, length = 50)
    private String actionCode;

    @Column(name = "action_name_en", nullable = false, length = 150)
    private String actionNameEn;

    @Column(name = "action_name_ar", length = 150)
    private String actionNameAr;

    @Column(name = "required_role", length = 100)
    private String requiredRole;

    /** Optional SpEL expression — when present must evaluate to true for transition to fire. */
    @Column(name = "condition_expr", length = 500)
    private String conditionExpr;

    /** Optional SLA — transition must occur within this many hours of entering from_state. */
    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "description_en", length = 500)
    private String descriptionEn;
}
