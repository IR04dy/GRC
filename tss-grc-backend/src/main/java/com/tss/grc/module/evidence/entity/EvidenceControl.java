package com.tss.grc.module.evidence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "evidence", name = "controls")
@Getter @Setter @NoArgsConstructor
public class EvidenceControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "framework_id", nullable = false)
    private Long frameworkId;

    @Column(name = "control_code", nullable = false, length = 50)
    private String controlCode;

    @Column(name = "name_en", nullable = false, length = 500)
    private String nameEn;

    @Column(name = "name_ar", length = 500)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
