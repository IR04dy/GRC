package com.tss.grc.module.ethics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "ethics", name = "case_actions")
@Getter @Setter @NoArgsConstructor
public class CaseAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "action_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime actionAt;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "is_confidential", nullable = false)
    private Boolean isConfidential = true;
}
