package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Generic typed link from a change request to any other entity in the platform.
 * Lets the change-management module integrate with risk, control, incident,
 * vendor, BCM plan, audit finding, policy, regulatory change, DPIA, KRI,
 * asset and evidence records without bespoke join tables.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_links")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;
    // RISK | CONTROL | INCIDENT | VENDOR | BCM_PLAN | AUDIT_FINDING | POLICY
    //   | REGULATORY_CHANGE | DPIA | KRI | ASSET | EVIDENCE

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "link_type", nullable = false, length = 30)
    private String linkType = "AFFECTS";
    // AFFECTS | TRIGGERED_BY | MITIGATES | TARGETS | REMEDIATES | DEPENDS_ON | INFORMS

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
