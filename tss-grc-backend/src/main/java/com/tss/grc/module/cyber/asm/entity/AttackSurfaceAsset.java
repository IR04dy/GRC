package com.tss.grc.module.cyber.asm.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One external attack-surface asset — Sprint 11 Attack Surface Management (ASM).
 *
 * <p>Represents a single internet-facing asset the CISO needs to keep
 * accountable: a web app, a public-facing API endpoint, an IP CIDR
 * carrying ATM traffic, a TLS certificate, an SMTP edge, etc.
 *
 * <p>Lifecycle state machine (enforced in {@code AttackSurfaceAssetService}):
 * <pre>
 *   DISCOVERED → IN_REVIEW → APPROVED → RETIRED
 *                          ↘ SHADOW_IT_PENDING → APPROVED | RETIRED
 * </pre>
 *
 * <p>Risk-score (0-100, persisted in {@link #riskScore}) is computed
 * from <strong>highest-open-finding severity</strong> ×
 * <strong>exposure</strong> × <strong>criticality</strong>. The
 * service layer recomputes after every finding mutation so the
 * dashboard ranking reflects current state.
 */
@Entity
@Table(schema = "cyber", name = "attack_surface_asset")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AttackSurfaceAsset extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "asset_code", nullable = false, length = 64)
    private String assetCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * WEB_APPLICATION | MOBILE_APP | API_ENDPOINT | DNS_RECORD | CERTIFICATE |
     * IP_RANGE | EMAIL_SERVER | FILE_SHARE | VPN_GATEWAY | CLOUD_BUCKET |
     * GIT_REPO | ADMIN_PORTAL | MARKETING_SITE | SHADOW_IT_SAAS
     */
    @Column(name = "asset_type", nullable = false, length = 40)
    private String assetType;

    @Column(name = "hostname", length = 255)
    private String hostname;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    /** HTTP | HTTPS | TCP | UDP | SSH | FTP | SMTP | DNS | ICMP */
    @Column(name = "protocol", length = 20)
    private String protocol;

    @Column(name = "cert_subject", length = 500)
    private String certSubject;

    @Column(name = "cert_issuer", length = 255)
    private String certIssuer;

    @Column(name = "cert_expires_at")
    private LocalDateTime certExpiresAt;

    @Column(name = "asset_owner_user_id", nullable = false)
    private Long assetOwnerUserId;

    @Column(name = "business_unit_en", length = 255)
    private String businessUnitEn;

    @Column(name = "business_unit_ar", length = 255)
    private String businessUnitAr;

    /** PUBLIC | PARTNER | INTERNAL | RESTRICTED */
    @Column(name = "exposure", nullable = false, length = 20)
    private String exposure;

    /** CRITICAL | HIGH | MEDIUM | LOW */
    @Column(name = "criticality", nullable = false, length = 20)
    private String criticality;

    /** DISCOVERED | IN_REVIEW | APPROVED | SHADOW_IT_PENDING | RETIRED */
    @Column(name = "lifecycle_stage", nullable = false, length = 40)
    private String lifecycleStage = "DISCOVERED";

    /**
     * MANUAL | TENABLE | CROWDSTRIKE | DNS_SCAN | CERT_TRANSPARENCY |
     * SHODAN | CCM_RUNNER | CUSTOMER_TICKET
     */
    @Column(name = "discovery_source", nullable = false, length = 40)
    private String discoverySource;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt = LocalDateTime.now();

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    /** Comma-separated free-form tags. */
    @Column(name = "tags", length = 500)
    private String tags;

    /** Computed risk score 0.00 – 100.00 — recomputed by service layer. */
    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;
}
