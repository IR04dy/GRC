package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "change_mgmt", name = "change_approvals")
@Getter @Setter @NoArgsConstructor
public class ChangeApproval {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approver_name", nullable = false, length = 255)
    private String approverName;

    @Column(name = "approver_role", length = 100)
    private String approverRole;

    @Column(name = "decision", nullable = false, length = 30)
    private String decision; // APPROVE | REJECT | DEFER | ABSTAIN

    @Column(name = "comments", columnDefinition = "NVARCHAR(MAX)")
    private String comments;

    @Column(name = "decided_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime decidedAt;
}
