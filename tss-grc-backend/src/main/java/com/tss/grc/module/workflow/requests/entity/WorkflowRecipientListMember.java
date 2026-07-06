package com.tss.grc.module.workflow.requests.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * WF-8 — one member of a {@link WorkflowRecipientList}. Mirrors the shape of a
 * {@link WorkflowRequestRecipient} (type + target + actor role + step) so expansion into a
 * request's recipient rows is a direct copy.
 */
@Entity
@Table(schema = "workflow", name = "recipient_list_members")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WorkflowRecipientListMember extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "list_id", nullable = false) private Long listId;

    @Column(name = "recipient_type", nullable = false, length = 20) private String recipientType;
    @Column(name = "recipient_id") private Long recipientId;
    @Column(name = "recipient_key", length = 120) private String recipientKey;
    @Column(name = "recipient_label", length = 200) private String recipientLabel;
    @Column(name = "actor_role", nullable = false, length = 30) private String actorRole = "ASSIGNEE";
    @Column(name = "step_no", nullable = false) private Integer stepNo = 1;
}
