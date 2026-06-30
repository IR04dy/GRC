package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Typed register linkage — ERM remediation epic E-G.
 *
 * <p>A first-class, typed cross-reference from a risk to another register
 * object — the business objective it threatens, the process / service / asset
 * it affects, the supplier or project it stems from, or a related incident /
 * control / obligation. Tier-1 ERM platforms expose this so a risk can be read
 * in its full operating context (and so impact analysis can traverse the graph).
 *
 * <p>{@code targetRef} is the human-facing label/code (always present);
 * {@code targetId} optionally pins the link to a concrete row in another module
 * when one exists. Kept deliberately lightweight (mirrors {@link RiskControl}).
 */
@Entity
@Table(schema = "risk", name = "risk_links")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    /** OBJECTIVE | PROCESS | SERVICE | ASSET | SUPPLIER | PROJECT | INCIDENT | CONTROL | OBLIGATION | OTHER */
    @Column(name = "link_type", nullable = false, length = 30)
    private String linkType;

    /** Human-facing label or external code of the linked object (always set). */
    @Column(name = "target_ref", nullable = false, length = 300)
    private String targetRef;

    /** Optional concrete row id in the target module, when the link points at one. */
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
