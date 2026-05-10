package com.tss.grc.module.compliance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Compliance framework dictionary entry — used as the anchor dimension for
 * policies, audit findings, controls and DPIAs (IA-1 framework anchor).
 */
@Entity
@Table(schema = "compliance", name = "frameworks")
@Getter @Setter @NoArgsConstructor
public class Framework {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "version", length = 40)
    private String version;

    @Column(name = "issuer", length = 255)
    private String issuer;

    @Column(name = "family", length = 60)
    private String family; // SECURITY | PRIVACY | BCM | FINANCIAL | LABOUR | TAX | TELECOM

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
