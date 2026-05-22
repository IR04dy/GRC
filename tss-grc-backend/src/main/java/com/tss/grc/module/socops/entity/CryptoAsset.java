package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Cryptographic asset inventory — TLS certificates, signing certs, HSMs,
 * SSH/service keys, KMS-managed keys, PGP keys.
 *
 * <p>Drives the certificate-expiry alert workflow. The {@code quantumSafe}
 * flag tracks post-quantum readiness — NCA's published direction is to
 * inventory crypto by quantum-safe vs not, and to migrate the latter
 * before 2030.
 */
@Entity
@Table(schema = "socops", name = "crypto_asset")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CryptoAsset extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "asset_code", nullable = false, length = 80) private String assetCode;
    @Column(name = "asset_type", nullable = false, length = 40) private String assetType;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "issued_by", length = 255) private String issuedBy;
    @Column(name = "subject_dn", length = 500) private String subjectDn;
    @Column(length = 255) private String fingerprint;
    @Column(length = 60) private String algorithm;
    @Column(name = "key_length") private Integer keyLength;
    @Column(name = "quantum_safe", nullable = false) private Boolean quantumSafe = false;
    @Column(name = "valid_from") private LocalDateTime validFrom;
    @Column(name = "valid_until") private LocalDateTime validUntil;
    @Column(name = "deployed_on", length = 500) private String deployedOn;
    @Column(length = 255) private String owner;
    @Column(name = "rotation_period_days") private Integer rotationPeriodDays;
    @Column(name = "last_rotated_at") private LocalDateTime lastRotatedAt;
    @Column(length = 40) private String classification;
    @Column(nullable = false, length = 30) private String status = "ACTIVE";
}
