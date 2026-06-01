package com.tss.grc.module.controlmap.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Generic, polymorphic control mapping: links any entity ({@code entity_type} +
 * {@code entity_id}) to a sub-control across frameworks. Optional and manually
 * maintained by the user (never auto-derived) — replace-set semantics.
 */
@Entity
@Table(schema = "evidence", name = "control_links")
@Getter @Setter @NoArgsConstructor
public class ControlLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "sub_control_id", nullable = false)
    private Long subControlId;

    /** How fully this entity satisfies the mapped control: FULL / PARTIAL / PLANNED. */
    @Column(name = "satisfaction", length = 20)
    private String satisfaction = "FULL";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
