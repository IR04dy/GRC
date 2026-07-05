package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * SOC-E — one concrete action taken on a threat-intel ({@link ThreatIntel}) row.
 *
 * <p>Before this, "actioning" an IOC was a single boolean flip on the parent row. An action is
 * now a first-class, appended record of WHAT the SOC did (block/scan/hunt/link-detection…) and
 * WHICH concrete platform target it touched — an {@code ASSET}, a {@code VULNERABILITY}, or a
 * {@code DETECTION} rule. A single IOC can carry many actions (blocked on a firewall asset AND
 * linked to the detection that watches for it), giving an auditable action history rather than
 * a lossy boolean.
 */
@Entity
@Table(schema = "socops", name = "threat_intel_action")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ThreatIntelAction extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "threat_intel_id", nullable = false) private Long threatIntelId;

    /** BLOCK / SCAN / HUNT / MONITOR / PATCH / BRIEF / LINK_DETECTION / REVIEWED / ESCALATE. */
    @Column(name = "action_type", nullable = false, length = 30) private String actionType;

    /** ASSET / VULNERABILITY / DETECTION / NONE — the kind of target this action links to. */
    @Column(name = "target_type", length = 20) private String targetType;
    @Column(name = "target_id") private Long targetId;
    /** Human label captured at action time so the link reads even if the target is later renamed. */
    @Column(name = "target_label", length = 300) private String targetLabel;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
    @Column(name = "actioned_by_user_id") private Long actionedByUserId;
    @Column(name = "actioned_by", length = 150) private String actionedBy;
    @Column(name = "actioned_at", nullable = false) private LocalDateTime actionedAt;
}
