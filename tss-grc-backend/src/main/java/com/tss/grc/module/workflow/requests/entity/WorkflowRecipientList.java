package com.tss.grc.module.workflow.requests.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * WF-8 — a named, reusable recipient set ("distribution list", §2.2 / §5). Composed once by an
 * org and expanded into a {@link WorkflowRequest}'s recipients on create, so a requester can
 * "send to a distribution list" instead of rebuilding the same recipients every time.
 */
@Entity
@Table(schema = "workflow", name = "recipient_lists")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WorkflowRecipientList extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 500) private String description;
    @Column(name = "is_active", nullable = false) private Short isActive = 1;
}
