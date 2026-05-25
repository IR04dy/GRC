package com.tss.grc.module.consumer.complaints.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Timeline row for a {@link Complaint} — state changes, customer
 * contacts, internal notes, SAMA notifications.
 *
 * <p>{@link #customerVisible} is the crucial flag: only rows with
 * {@code customer_visible=true} are returned by the public tracking
 * endpoint. Internal investigation notes (sub-processor analysis,
 * fraud-ring linkages, etc.) stay hidden.
 */
@Entity
@Table(schema = "consumer", name = "complaint_update")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ComplaintUpdate extends BaseEntity {

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    /**
     * STATUS_CHANGE | CUSTOMER_CONTACT | INTERNAL_NOTE |
     * EVIDENCE_REQUEST | SAMA_NOTIFICATION.
     */
    @Column(name = "update_type", nullable = false, length = 32)
    private String updateType;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", length = 32)
    private String toStatus;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;

    @Column(name = "actor", length = 128)
    private String actor;

    @Column(name = "customer_visible", nullable = false)
    private Boolean customerVisible = false;

    @Column(name = "occurred_on", nullable = false)
    private LocalDateTime occurredOn = LocalDateTime.now();
}
