package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "privacy", name = "dpia_mitigation")
@Getter @Setter @NoArgsConstructor
public class DpiaMitigation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dpia_id", nullable = false)
    private Long dpiaId;

    @Column(name = "mitigation_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String mitigationEn;

    @Column(name = "mitigation_ar", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationAr;

    /** PLANNED | IN_PROGRESS | IMPLEMENTED | VERIFIED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PLANNED";

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
