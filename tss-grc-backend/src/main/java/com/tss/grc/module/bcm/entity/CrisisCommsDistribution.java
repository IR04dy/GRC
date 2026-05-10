package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "crisis_comms_distributions")
@Getter @Setter @NoArgsConstructor
public class CrisisCommsDistribution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "audience_id", nullable = false)
    private Long audienceId;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "subject_snapshot", length = 500)
    private String subjectSnapshot;

    @Column(name = "body_snapshot", columnDefinition = "NVARCHAR(MAX)")
    private String bodySnapshot;

    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "sent_by_id")
    private Long sentById;

    /** SIMULATED | SENT | DELIVERED | FAILED */
    @Column(name = "delivery_status", nullable = false, length = 30)
    private String deliveryStatus = "SIMULATED";

    @Column(name = "delivery_summary", columnDefinition = "NVARCHAR(MAX)")
    private String deliverySummary;
}
