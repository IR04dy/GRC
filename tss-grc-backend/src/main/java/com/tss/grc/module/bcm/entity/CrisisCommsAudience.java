package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(schema = "bcm", name = "crisis_comms_audiences")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisCommsAudience {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "audience_role", nullable = false, length = 100)
    private String audienceRole;

    @Column(name = "contacts_csv", columnDefinition = "NVARCHAR(MAX)")
    private String contactsCsv;

    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
