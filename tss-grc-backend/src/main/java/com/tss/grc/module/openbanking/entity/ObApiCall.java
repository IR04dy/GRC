package com.tss.grc.module.openbanking.entity;

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
 * Append-only audit row per regulated API call against the bank's
 * Open Banking surface.
 *
 * <p>Captures the FAPI 2.0 + DPoP envelope (TPP + consent + scopes)
 * plus the operational telemetry (latency, status, bytes) so the
 * Compliance Officer can answer SAMA's quarterly OB return + handle
 * subject-access requests + investigate disputed transactions.
 */
@Entity
@Table(schema = "openbanking", name = "ob_api_call")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObApiCall extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "tpp_id", nullable = false)
    private Long tppId;

    /** Consent reference (may be null for /.well-known + discovery endpoints). */
    @Column(name = "consent_reference", length = 96)
    private String consentReference;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** HTTP method: GET / POST / PUT / DELETE. */
    @Column(name = "http_method", nullable = false, length = 8)
    private String httpMethod;

    /** Endpoint path (e.g. {@code /open-banking/v3.1/accounts/{id}/transactions}). */
    @Column(name = "endpoint", nullable = false, length = 512)
    private String endpoint;

    /** HTTP status returned. */
    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    /** Latency in milliseconds. */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** Comma-separated scopes presented on the call. */
    @Column(name = "scopes_used", length = 512)
    private String scopesUsed;

    /** Hashed customer identifier (for AIS) — never raw. */
    @Column(name = "customer_hash", length = 128)
    private String customerHash;

    /** Hashed source IP (privacy-preserving). */
    @Column(name = "source_ip_hash", length = 64)
    private String sourceIpHash;

    /** FAPI-Interaction-Id (request correlation header). */
    @Column(name = "interaction_id", length = 64)
    private String interactionId;

    /** Bytes sent on the response. */
    @Column(name = "response_bytes")
    private Integer responseBytes;

    /** Error reason text on non-2xx responses. */
    @Column(name = "error_reason", length = 512)
    private String errorReason;
}
