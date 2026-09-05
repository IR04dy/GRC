package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "critical_processes")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CriticalProcess {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "process_name_en", nullable = false, length = 255)
    private String processNameEn;

    @Column(name = "process_name_ar", length = 255)
    private String processNameAr;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "criticality", length = 20)
    private String criticality = "HIGH";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_by")
    private Long deletedBy;
}
